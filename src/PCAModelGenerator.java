import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import weka.attributeSelection.PrincipalComponents;
import weka.core.Instances;

public class PCAModelGenerator {
    public static BufferedReader readDataFile(String filename) {
        BufferedReader inputReader = null;
        try {
            inputReader = new BufferedReader(new FileReader(filename));
        } catch (FileNotFoundException ex) {
            System.err.println("File not found: " + filename);
        }
        
        return inputReader;
    }

	public static void main(String[] args) throws Exception {
    	    	
    	Instances data = new Instances(readDataFile("./DATA.arff"));
    	data.setClassIndex(data.numAttributes() - 1);

    	PrincipalComponents pca = new PrincipalComponents();
    	pca.buildEvaluator(data);
    	weka.core.SerializationHelper.write("./pca_DATA.model", pca);
    	System.out.println("Model for " + data + " was created!");
    	
	}

}
