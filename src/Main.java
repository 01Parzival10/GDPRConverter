import mdpa.gdpr.metamodel.GDPR.*;
public class Main {

	public static void main(String[] args) {
		String s1 = "C:\\Users\\Huell\\Desktop\\Newfolder\\Test.gdpr";
		String s2 = "C:\\Users\\Huell\\Desktop\\Newfolder\\Test.dataflowdiagram";
		String s3 = "C:\\Users\\Huell\\Desktop\\Newfolder\\onlineshop.datadictionary";
		String s4 = "C:\\Users\\Huell\\Desktop\\Newfolder\\Test.tracemodel";
		GDPR2DFD trans = new GDPR2DFD(s1, s2, s3, s4);
		trans.transform();
		
		/*String s3 = "C:\\Users\\Huell\\Desktop\\Newfolder\\Test.gdpr";
		String s1 = "C:\\Users\\Huell\\Desktop\\Newfolder\\onlineshop.dataflowdiagram";
		String s2 = "C:\\Users\\Huell\\Desktop\\Newfolder\\onlineshop.datadictionary";
		String s4 = "C:\\Users\\Huell\\Desktop\\Newfolder\\Test.tracemodel";
		DFD2GDPR trans = new DFD2GDPR(s1, s2, s3, s4);
		trans.transform();*/
	}

}
