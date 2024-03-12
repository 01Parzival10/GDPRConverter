import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;


import org.dataflowanalysis.dfd.datadictionary.DataDictionary;
import org.dataflowanalysis.dfd.datadictionary.Label;
import org.dataflowanalysis.dfd.datadictionary.LabelType;
import org.dataflowanalysis.dfd.datadictionary.datadictionaryPackage;
import org.dataflowanalysis.dfd.dataflowdiagram.DataFlowDiagram;
import org.dataflowanalysis.dfd.dataflowdiagram.External;
import org.dataflowanalysis.dfd.dataflowdiagram.Flow;
import org.dataflowanalysis.dfd.dataflowdiagram.Node;
import org.dataflowanalysis.dfd.dataflowdiagram.Process;
import org.dataflowanalysis.dfd.dataflowdiagram.Store;
import org.dataflowanalysis.dfd.dataflowdiagram.dataflowdiagramPackage;
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
import tracemodel.FlowElement;
import tracemodel.Trace;
import tracemodel.TraceModel;
import tracemodel.TracemodelFactory;

public class DFD2GDPR {	
	private DataFlowDiagram dfd;
	private DataDictionary dd;
	private LegalAssessmentFacts laf;
	private TraceModel tracemodel;
	
	private GDPRFactory gdprFactory;
	private TracemodelFactory traceModelFactory;
	private Map<Node, Processing> mapNodeToProcessing = new HashMap<>();
	private Map<String, Entity> mapIdToElement = new HashMap<>();
	
	private Set<Label> resolvedLinkageLabel = new HashSet<>();
	
	private String gdprFile;
	private String traceModelFile;
	
	private ResourceSet rs;
	
	private Resource ddResource;
	
	public DFD2GDPR(String dfdFile, String ddFile, String gdprFile, String traceModelFile) {
		this.gdprFile = gdprFile;
		this.traceModelFile = traceModelFile;
		
		rs = new ResourceSetImpl();
		rs.getResourceFactoryRegistry().getExtensionToFactoryMap().put(Resource.Factory.Registry.DEFAULT_EXTENSION, new XMIResourceFactoryImpl());
		rs.getPackageRegistry().put(dataflowdiagramPackage.eNS_URI, dataflowdiagramPackage.eINSTANCE);
		rs.getPackageRegistry().put(datadictionaryPackage.eNS_URI, datadictionaryPackage.eINSTANCE);
		
		gdprFactory = GDPRFactory.eINSTANCE;
		laf = gdprFactory.createLegalAssessmentFacts();
		
		traceModelFactory = TracemodelFactory.eINSTANCE;
		tracemodel = traceModelFactory.createTraceModel();
		
		Resource dfdResource = rs.getResource(URI.createFileURI(dfdFile), true);
		ddResource = rs.getResource(URI.createFileURI(ddFile), true);
		
		dd = (DataDictionary) ddResource.getContents().get(0);
		dfd = (DataFlowDiagram) dfdResource.getContents().get(0);	
		
	}
	
	public void transform() {
		dfd.getNodes().stream().forEach(node -> {
			Processing processing = resolveProcessingTypeLabel(node);
			resolveElementLabel(processing, node.getProperties());
			resolveLinkLabel(node.getProperties());

			processing.setEntityName(node.getEntityName());
			processing.setId(node.getId());
			
			mapNodeToProcessing.put(node, processing);
			
			laf.getProcessing().add(processing);
		});
		
		
		dfd.getFlows().stream().forEach(flow -> transformFlow(flow));
		
		dfd.getFlows().stream().forEach(flow -> {
			FlowElement flowElement = traceModelFactory.createFlowElement();
			flowElement.setFlow(flow);
			flowElement.setDestinationID(flow.getDestinationNode().getId());
			flowElement.setSourceID(flow.getSourceNode().getId());
			tracemodel.getFlowList().add(flowElement);
		});
		
		mapNodeToProcessing.forEach((node, processing) -> {
			Trace trace = traceModelFactory.createTrace();
			trace.setNode(node);
			trace.setProcessing(processing);
			tracemodel.getTracesList().add(trace);
		});
		
		
		List<LabelType> gdprLabels = new ArrayList<>();
		dd.getLabelTypes().forEach(lt -> {
			String name = lt.getEntityName();
			if (name.equals("GDPRElement") || name.equals("GDPRNode") || name.equals("GDPRLink"))
				gdprLabels.add(lt);
		});		
		dd.getLabelTypes().removeAll(gdprLabels);
		
		Resource gdprResource = createAndAddResource(gdprFile, new String[] {"dataflowdiagram"} ,rs);
		Resource tmResource = createAndAddResource(traceModelFile, new String[] {"dataflowdiagram"} ,rs);
		
		gdprResource.getContents().add(laf);
		tmResource.getContents().add(tracemodel);
		
		saveResource(gdprResource);
		saveResource(tmResource);
		saveResource(ddResource);
	}
	
	private Processing resolveProcessingTypeLabel(Node node) {
		List<String> filteredLabels = node.getProperties().stream()
				.map(label -> label.getEntityName())
				.filter(name -> name.startsWith("GDPR::ofType:"))
				.toList();
		
		if (filteredLabels.size() == 0) {
			if (node instanceof Store) return gdprFactory.createStoring();
			if (node instanceof External) return gdprFactory.createCollecting();
			if (node instanceof Process) return gdprFactory.createProcessing();
		}	
		
		String type = filteredLabels.get(0).replace("GDPR::ofType:", "").replace("Impl", "");
			
		
		if (type.equals(Useage.class.getSimpleName())) {
			return gdprFactory.createUseage();
		} else if (type.equals(Transfering.class.getSimpleName())) {
			return gdprFactory.createTransfering();
		} else if (type.equals(Storing.class.getSimpleName())) {
			return gdprFactory.createStoring();
		} else if (type.equals(Collecting.class.getSimpleName())) {
			return gdprFactory.createCollecting();
		} else if (type.equals(Processing.class.getSimpleName())) {
			return gdprFactory.createProcessing();
		} else {
			throw new IllegalArgumentException("Processing Type Label contains invalid Type");
		}
	}
	
	private void transformFlow (Flow flow) {
		Processing source = mapNodeToProcessing.get(flow.getSourceNode());
		Processing destination = mapNodeToProcessing.get(flow.getDestinationNode());
		
		source.getFollowingProcessing().add(destination);
	}
	
	private void resolveLinkLabel (List<Label> properties) {
		properties.stream()
		.filter(label -> label.getEntityName().startsWith("GDPR::"))
		.forEach(label -> {
			if (resolvedLinkageLabel.contains(label)) return;
			
			String[] substring = label.getEntityName().replace("GDPR::", "").split("::");
			if (substring.length != 3) return;
			
			Entity firstElement = resolveAndCreateElementType(substring[0]);
			Entity secondElement = resolveAndCreateElementType(substring[2]);
			String reference = substring[1];
			
			switch (reference) {
				case "Consentee":
					((Consent)firstElement).setConsentee((NaturalPerson)secondElement);
					break;
				case "ContractParty":
					((PerformanceOfContract)firstElement).getContractingParty().add((Role)secondElement);
					break;
				case "ForPurpose":
					((LegalBasis)firstElement).getForPurpose().add((Purpose)secondElement);
					break;
				case "ForData":
					((LegalBasis)firstElement).setPersonalData((PersonalData)secondElement);
					break;
				case "Reference":
					((PersonalData)firstElement).getDataReferences().add((NaturalPerson)secondElement);
					break;
				default:
					throw new IllegalArgumentException("GPDPR link label reference does not match any references");
			}
			
			resolvedLinkageLabel.add(label);
		});
	}
	
	private void resolveElementLabel(Processing processing, List<Label> properties) {
		properties.stream()
		.filter(label -> ((LabelType)label.eContainer()).getEntityName().equals("GDPRElement"))
		.forEach(label -> {
			String string = label.getEntityName().replaceFirst("GDPR::", "");
			String[] substring = string.split("::");
			String reference = substring[0];
			
			Entity entity = resolveAndCreateElementType(substring[1]);
			
			switch (reference) {
				case "InputData":
					processing.getInputData().add((Data)entity);
					break;
				case "OutputData":
					processing.getOutputData().add((Data)entity);
					break;
				case "LegalBasis":
					processing.getOnTheBasisOf().add((LegalBasis)entity);
					break;
				case "Purpose":
					processing.getPurpose().add((Purpose)entity);
					break;
				case "Responsible":
					processing.setResponsible((Role)entity);
					break;
				default:
					throw new IllegalArgumentException("Element Label reference does not match any reference");
			}			
		});		
	}
	
	private Entity resolveAndCreateElementType(String string) {
		String[] substring = string.split(":");
		String type = substring[0].replace("Impl", "");
		String name = substring[1];
		String id = substring[2];
		
		if (mapIdToElement.containsKey(id)) return mapIdToElement.get(id);
		
		Entity entity; 	
		
		if (type.equals(Consent.class.getSimpleName())) {
			entity = gdprFactory.createConsent();
			laf.getLegalBases().add((LegalBasis)entity);
		} else if (type.equals(PerformanceOfContract.class.getSimpleName())) {
			entity = gdprFactory.createPerformanceOfContract();
			laf.getLegalBases().add((LegalBasis)entity);
		} else if (type.equals(Obligation.class.getSimpleName())) {
			entity = gdprFactory.createObligation();
			laf.getLegalBases().add((LegalBasis)entity);
		} else if (type.equals(ExerciceOfPublicAuthority.class.getSimpleName())) {
			entity = gdprFactory.createExerciceOfPublicAuthority();
			laf.getLegalBases().add((LegalBasis)entity);
		} else if (type.equals(Data.class.getSimpleName())) {
			entity = gdprFactory.createData();
			laf.getData().add((Data)entity);
		} else if (type.equals(PersonalData.class.getSimpleName())) {
			entity = gdprFactory.createPersonalData();
			laf.getData().add((Data)entity);
		} else if (type.equals(Purpose.class.getSimpleName())) {
			entity = gdprFactory.createPurpose();
			laf.getPurposes().add((Purpose)entity);
		} else if (type.equals(Controller.class.getSimpleName())) {			
			entity = gdprFactory.createController();
			((Role)entity).setName(name);
			laf.getInvolvedParties().add((Role)entity);
		} else if (type.equals(NaturalPerson.class.getSimpleName())) {
			entity = gdprFactory.createNaturalPerson();
			((Role)entity).setName(name);
			laf.getInvolvedParties().add((Role)entity);
		} else if (type.equals(ThirdParty.class.getSimpleName())) {
			entity = gdprFactory.createThirdParty();
			((Role)entity).setName(name);
			laf.getInvolvedParties().add((Role)entity);
		} else {
			throw new IllegalArgumentException("Element Label Type does not match any reference class");
		}
		
		entity.setEntityName(name);
		entity.setId(id);
		
		mapIdToElement.put(id, entity);
		
		return entity;
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
