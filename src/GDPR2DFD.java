import org.dataflowanalysis.dfd.datadictionary.*;
import org.dataflowanalysis.dfd.dataflowdiagram.*;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceImpl;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.XMLResource;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.eclipse.emf.ecore.xmi.impl.XMLResourceFactoryImpl;

import mdpa.gdpr.metamodel.GDPR.*;
import tools.mdsd.modelingfoundations.identifier.Entity;
import tracemodel.TraceModel;
import tracemodel.TracemodelPackage;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.io.IOException;
import java.util.ArrayList;

public class GDPR2DFD {
	private DataFlowDiagram dfd;
	private DataDictionary dd;
	private LegalAssessmentFacts laf;
	private TraceModel tracemodel;
	
	private dataflowdiagramFactory dfdFactory;
	private datadictionaryFactory ddFactory;
	
	private LabelType gdprElementsLabelType;
	private LabelType gdprNodeLabelType;
	private LabelType gdprLinkLabelType;
	
	private Map<Processing, Node> mapProcessingToNode = new HashMap<>();	
	private Map<String, Label> mapElementIdAndReferenceToLabel = new HashMap<>();
	private Map<Entity, List<Label>> mapElementToLinkageLabels = new HashMap<>();
	
	private String dfdFile;
	private String ddFile;
	
	private ResourceSet rs;
	
	private Resource ddResource;
	
	/**
	 * Creates Transformation with tracemodel to recreate DFD instance
	 * @param gdprFile Location of the GDPR instance
	 * @param dfdFile Location of where to save the new DFD instance
	 * @param ddFile Location of the Data Dictionary instance
	 * @param traceModelFile Location of the TraceModel instance
	 */
	public GDPR2DFD(String gdprFile, String dfdFile, String ddFile, String traceModelFile) {
		this.dfdFile = dfdFile;
		this.ddFile = ddFile;
		
		rs = new ResourceSetImpl();
		rs.getResourceFactoryRegistry().getExtensionToFactoryMap().put(Resource.Factory.Registry.DEFAULT_EXTENSION, new XMIResourceFactoryImpl());
		rs.getPackageRegistry().put(GDPRPackage.eNS_URI, GDPRPackage.eINSTANCE);
		rs.getPackageRegistry().put(TracemodelPackage.eNS_URI, TracemodelPackage.eINSTANCE);
		
		dfdFactory = dataflowdiagramFactory.eINSTANCE;
		ddFactory = datadictionaryFactory.eINSTANCE;
		dfd = dfdFactory.createDataFlowDiagram();
		
		Resource gdprResource = rs.getResource(URI.createFileURI(gdprFile), true);
		Resource ddResource = rs.getResource(URI.createFileURI(ddFile), true);
		Resource tmResource = rs.getResource(URI.createFileURI(traceModelFile), true);
		
		laf = (LegalAssessmentFacts) gdprResource.getContents().get(0);
		dd = (DataDictionary) ddResource.getContents().get(0);		
		tracemodel = (TraceModel) tmResource.getContents().get(0);
	}
	
	/**
	 * Creates Transformation for initial transformations from the GDPR into the DFD metamodel
	 * @param gdprFile Location of the gdpr instance
	 * @param dfdFile Location of where to save the new DFD instance
	 * @param ddFileLocation of where to save the new DD instance
	 */
	public GDPR2DFD(String gdprFile, String dfdFile, String ddFile) {
		this.dfdFile = dfdFile;
		this.ddFile = ddFile;
		
		rs = new ResourceSetImpl();
		rs.getResourceFactoryRegistry().getExtensionToFactoryMap().put(Resource.Factory.Registry.DEFAULT_EXTENSION, new XMIResourceFactoryImpl());
		rs.getPackageRegistry().put(GDPRPackage.eNS_URI, GDPRPackage.eINSTANCE);
		
		dfdFactory = dataflowdiagramFactory.eINSTANCE;
		ddFactory = datadictionaryFactory.eINSTANCE;
		dfd = dfdFactory.createDataFlowDiagram();
		dd = ddFactory.createDataDictionary();	
		
		Resource gdprResource = rs.getResource(URI.createFileURI(gdprFile), true);
		
		laf = (LegalAssessmentFacts) gdprResource.getContents().get(0);					
	}
	
	/**
	 * Performs the transformation with the information provided in the constructor
	 */
	public void transform() {
		createLabelTypes();
		
		if (tracemodel != null) handleTraceModel();
		
		laf.getData().stream().filter(d -> d instanceof PersonalData).forEach(pd -> {
			fillLinkageLabelMapForPersonalData((PersonalData)pd);
		});
		
		laf.getLegalBases().stream().forEach(lb -> {
			fillLinkageLabelMapForLegalBasis(lb);
		});
		
		laf.getProcessing().stream().forEach(p -> {
			Node node = convertProcessing(p);
			mapProcessingToNode.put(p, node);
			dfd.getNodes().add(node);
		});
		
		laf.getProcessing().stream().forEach(p -> {
			dfd.getFlows().addAll(createFlows(p));
		});
		
		Resource dfdResource = createAndAddResource(dfdFile, new String[] {"dataflowdiagram"} ,rs);
		if (ddResource == null)  {
			ddResource = createAndAddResource(ddFile, new String[] {"datadictionary"} ,rs);
			ddResource.getContents().add(dd);
		}
		
		dfdResource.getContents().add(dfd);		
		
		saveResource(dfdResource);
		saveResource(ddResource);
	}
	
	
	/**
	 * Creates the labeltypes that hold the GDPR instance specific information
	 */
	private void createLabelTypes() {
		gdprElementsLabelType = ddFactory.createLabelType();
		gdprNodeLabelType = ddFactory.createLabelType();
		gdprLinkLabelType = ddFactory.createLabelType();
		
		gdprElementsLabelType.setEntityName("GDPRElement");
		gdprNodeLabelType.setEntityName("GDPRNode");
		gdprLinkLabelType.setEntityName("GDPRLink");
		
		dd.getLabelTypes().add(gdprElementsLabelType);
		dd.getLabelTypes().add(gdprNodeLabelType);
		dd.getLabelTypes().add(gdprLinkLabelType);
	}
	
	/**
	 * Creates all flows outgoing from a processing from following processing or pulls them from the tracemodel if present
	 * @param processing Source for the created Nodes
	 * @return All flows going out from the source node
	 */
	private List<Flow> createFlows(Processing processing) {
		if (tracemodel != null) {
			return tracemodel.getFlowList()
					.stream()
					.filter(fe -> fe.getSourceID().equals(processing.getId()))
					.map(fe -> fe.getFlow())
					.toList();
		}
		
		List<Flow> flows = new ArrayList<>();
		
		Node sourceNode = mapProcessingToNode.get(processing);
		for (Processing followingProcessing : processing.getFollowingProcessing()) {
			Node destinationNode = mapProcessingToNode.get(followingProcessing);
			
			List<Flow> individualFlows = new ArrayList<>();
			
			if (tracemodel != null) {
				individualFlows = tracemodel.getFlowList().stream().filter(fE -> {
					return fE.getSourceID().equals(sourceNode.getId()) && fE.getDestinationID().equals(destinationNode.getId());
				}).map(fE -> fE.getFlow()).toList();
			}
			
			if (individualFlows.isEmpty()) {
				Flow flow = dfdFactory.createFlow();			
				
				Pin inputPin = ddFactory.createPin();
				Pin outputPin = ddFactory.createPin();
				sourceNode.getBehaviour().getOutPin().add(outputPin);
				destinationNode.getBehaviour().getInPin().add(inputPin);
				
				flow.setSourceNode(sourceNode);
				flow.setDestinationNode(destinationNode);
				flow.setSourcePin(outputPin);
				flow.setDestinationPin(inputPin);
				
				flows.add(flow);
			} else {
				flows.addAll(individualFlows);
			}
		}
		
		return flows;
	}
	
	/**
	 * Fills the processingToNode Map in case the tracemodel is present
	 */
	private void handleTraceModel() {
		tracemodel.getTracesList().forEach(t -> {
			mapProcessingToNode.put(t.getProcessing(), t.getNode());
		});
	}
	
	/**
	 * Transforms a processing into a node or pulls the node from the tracemodel if present
	 * @param processing Processing to be transformed
	 * @return Node that was created or pulled from the tracemodel
	 */
	private Node convertProcessing(Processing processing) {
		Node node;
		
		String name = processing.getEntityName();
		
		if (mapProcessingToNode.containsKey(processing)) {
			node = mapProcessingToNode.get(processing);
		} else if (processing instanceof Collecting) {
			node = dfdFactory.createExternal();;
		} else if (processing instanceof Storing) {
			node = dfdFactory.createStore();
		} else if (processing instanceof Useage) {
			node = dfdFactory.createProcess();
		} else if (processing instanceof Transfering) {
			node = dfdFactory.createProcess();
		} else {
			node = dfdFactory.createProcess();
		}
		
		node.getProperties().add(createTypeLabel(processing));
		
		if (node.getBehaviour() == null) {
			Behaviour behaviour = ddFactory.createBehaviour();
			behaviour.setEntityName(name + " Behaviour");
			node.setBehaviour(behaviour);
			dd.getBehaviour().add(behaviour);
		}
		
		processing.getOnTheBasisOf().stream().forEach(lb -> {
			node.getProperties().add(createElementLabel(lb, "LegalBasis"));
			node.getProperties().addAll(mapElementToLinkageLabels.get(lb));
		});
		
		processing.getPurpose().stream().forEach(p -> {
			node.getProperties().add(createElementLabel(p, "Purpose"));
		});
		
		processing.getInputData().stream().forEach(iD -> {
			node.getProperties().add(createElementLabel(iD, "InputData"));
			node.getProperties().addAll(mapElementToLinkageLabels.get(iD));
		});
		
		processing.getOutputData().stream().forEach(oD -> {
			node.getProperties().add(createElementLabel(oD, "OutputData"));
			node.getProperties().addAll(mapElementToLinkageLabels.get(oD));
		});
		
		if (processing.getResponsible() != null)
		node.getProperties().add(createElementLabel(processing.getResponsible(), "Responsible"));
		
		node.setId(processing.getId());
		node.setEntityName(name);
		
		return node;		
	}
	
	/**
	 * Created the linkage labels for the legal basis and saves it in the map
	 * @param legalBasis The legal basis for which the labels are created
	 */
	private void fillLinkageLabelMapForLegalBasis(LegalBasis legalBasis) {
		List<Label> labels = new ArrayList<>();
		if (legalBasis instanceof Consent) {
			labels.add(createLinkageLabel(legalBasis, ((Consent)legalBasis).getConsentee(), "Consentee"));
		} else if (legalBasis instanceof PerformanceOfContract) {
			((PerformanceOfContract)legalBasis).getContractingParty().stream().forEach(cp -> {
				labels.add(createLinkageLabel(legalBasis, cp, "ContractParty"));
			});
		}
		
		legalBasis.getForPurpose().stream().forEach(p -> {
			labels.add(createLinkageLabel(legalBasis, p, "ForPurpose"));
		});
		
		if (legalBasis.getPersonalData() != null) {
			labels.add(createLinkageLabel(legalBasis, legalBasis.getPersonalData(), "ForData"));
		}
		
		mapElementToLinkageLabels.put(legalBasis, labels);
	}
	
	/**
	 * Created the linkage labels for the personal data and saves it in the map
	 * @param PersonalData The legal basis for which the labels are created
	 */
	private void fillLinkageLabelMapForPersonalData(PersonalData personalData) {
		List<Label> labels = new ArrayList<>();
		
		personalData.getDataReferences().stream().forEach(dr -> {
			labels.add(createLinkageLabel(personalData, dr, "Reference"));
		});
		
		mapElementToLinkageLabels.put(personalData, labels);
	}
	
	
	/**
	 * Creates the label storing the processing type
	 * @param processing Processing whichs type is stored
	 * @return Label storing the type
	 */
	private Label createTypeLabel(Processing processing) {
		Label label = ddFactory.createLabel();		
		label.setEntityName("GDPR::ofType:" + processing.getClass().getSimpleName());
		gdprNodeLabelType.getLabel().add(label);
		return label;
	}
	
	/**
	 * Returns a label representing the Entity and its relationship to the processing element and creates it if necessary
	 * @param entity Entity to be represented by the label
	 * @param reference Reference representing the entiy and its relationship
	 * @return Creatd Label
	 */
	private Label createElementLabel(Entity entity, String reference) {
		if (mapElementIdAndReferenceToLabel.containsKey(entity.getId() + reference)) {
			return mapElementIdAndReferenceToLabel.get(entity.getId() + reference);		
		}
		
		Label label = ddFactory.createLabel();	
		StringBuilder builder = new StringBuilder();
		builder.append("GDPR::");
		builder.append(reference);
		builder.append("::");
		builder.append(entity.getClass().getSimpleName() + ":" + entity.getEntityName() + ":" + entity.getId());
		label.setEntityName(builder.toString());
		
		gdprElementsLabelType.getLabel().add(label);
		mapElementIdAndReferenceToLabel.put(entity.getId() + reference, label);
		
		return label;
	}
	
	/**
	 * Creates linkage label between two entities.
	 * @param firstEntity Entity holding the reference
	 * @param secondEntity References Entity
	 * @param linkType Type of reference
	 * @return Created Label
	 */
	private Label createLinkageLabel(Entity firstEntity, Entity secondEntity, String linkType) {
		Label label = ddFactory.createLabel();		
		StringBuilder builder = new StringBuilder();
		builder.append("GDPR::");
		builder.append(firstEntity.getClass().getSimpleName() + ":" + firstEntity.getEntityName() + ":" + firstEntity.getId());
		builder.append("::").append(linkType).append("::");
		builder.append(secondEntity.getClass().getSimpleName() + ":" + secondEntity.getEntityName() + ":" + secondEntity.getId());
		
		label.setEntityName(builder.toString());
		
		gdprLinkLabelType.getLabel().add(label);
		
		return label;
	}
	
	//Copied from https://sdq.kastel.kit.edu/wiki/Creating_EMF_Model_instances_programmatically
		@SuppressWarnings({ "rawtypes", "unchecked" })
		private Resource createAndAddResource(String outputFile, String[] fileextensions, ResourceSet rs) {
		     for (String fileext : fileextensions) {
		        rs.getResourceFactoryRegistry().getExtensionToFactoryMap().put(fileext, new XMLResourceFactoryImpl());
		     }		
		     URI uri = URI.createFileURI(outputFile);
		     Resource resource = rs.createResource(uri);
		     ((ResourceImpl)resource).setIntrinsicIDToEObjectMap(new HashMap());
		     return resource;
		  }
		
		@SuppressWarnings({"unchecked", "rawtypes"})
		 private void saveResource(Resource resource) {
		     Map saveOptions = ((XMLResource)resource).getDefaultSaveOptions();
		     saveOptions.put(XMLResource.OPTION_CONFIGURATION_CACHE, Boolean.TRUE);
		     saveOptions.put(XMLResource.OPTION_USE_CACHED_LOOKUP_TABLE, new ArrayList());
		     try {
		        resource.save(saveOptions);
		     } catch (IOException e) {
		        throw new RuntimeException(e);
		     }
		}
}
