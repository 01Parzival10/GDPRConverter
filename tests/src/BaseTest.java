import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class BaseTest {
	@BeforeEach
	public void cleanUp() {
		
	}
	
	@Test
	public void testGDPR2DFDForFirstIteration() {
		String gdprFile = "C:\\Users\\Huell\\Desktop\\Newfolder\\Test.gdpr";
		String dfdFile = "C:\\Users\\Huell\\Desktop\\Newfolder\\Test.dataflowdiagram";
		String ddFile = "C:\\Users\\Huell\\Desktop\\Newfolder\\onlineshop.datadictionary";
		
		GDPR2DFD trans = new GDPR2DFD(gdprFile, dfdFile, ddFile);
		trans.transform();
	}
	@Test
	public void testGDPR2DFDForContinousIteration() {
		String gdprFile = "C:\\Users\\Huell\\Desktop\\Newfolder\\Test.gdpr";
		String dfdFile = "C:\\Users\\Huell\\Desktop\\Newfolder\\Test.dataflowdiagram";
		String ddFile = "C:\\Users\\Huell\\Desktop\\Newfolder\\onlineshop.datadictionary";
		String tmFile = "";
		
		GDPR2DFD trans = new GDPR2DFD(gdprFile, dfdFile, ddFile, tmFile);
		trans.transform();
	}
	
	@Test
	public void testGDPR2DFDForFirstIteration() {
		String gdprFile = "C:\\Users\\Huell\\Desktop\\Newfolder\\Test.gdpr";
		String dfdFile = "C:\\Users\\Huell\\Desktop\\Newfolder\\Test.dataflowdiagram";
		String ddFile = "C:\\Users\\Huell\\Desktop\\Newfolder\\onlineshop.datadictionary";
		String tmFile = "";
		
		DFD2GDPR trans = new DFD2GDPR(dfdFile, ddFile, gdprFile, tmFile);
		trans.transform();
	}
	
	@Test
	public void testGDPR2DFDForContinousIteration() {
		String gdprFile = "C:\\Users\\Huell\\Desktop\\Newfolder\\Test.gdpr";
		String dfdFile = "C:\\Users\\Huell\\Desktop\\Newfolder\\Test.dataflowdiagram";
		String ddFile = "C:\\Users\\Huell\\Desktop\\Newfolder\\onlineshop.datadictionary";
		String tmFile = "";
		
		DFD2GDPR trans = new DFD2GDPR(dfdFile, ddFile, gdprFile, tmFile);
		trans.transform();
	}
}
