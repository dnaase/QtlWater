/**
 * QtlWater.java
 * Oct 23, 2015
 * 12:11:01 PM
 * yaping    lyping1986@gmail.com
 */
package main.java.edu.mit.compbio.qrf;



import hex.tree.gbm.GBM;
import hex.tree.gbm.GBMModel;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
















import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.api.java.function.Function;
import org.apache.spark.api.java.function.Function2;
import org.apache.spark.h2o.H2OContext;
import org.apache.spark.ml.Model;
import org.apache.spark.mllib.regression.LabeledPoint;
import org.apache.spark.sql.DataFrame;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.RowFactory;
import org.apache.spark.sql.SQLContext;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.StructField;
import org.apache.spark.sql.types.StructType;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import water.AutoBuffer;
import water.H2O;
import water.Job;
import water.Key;
import water.api.ModelImportV3;
import water.fvec.Frame;
import water.fvec.H2OFrame;
import water.serial.ObjectTreeBinarySerializer;
import water.util.FileUtils;



public class QtlWater implements Serializable{


	/**
	 * 
	 */
	private static final long serialVersionUID = 4156495694998275452L;


	/**
	 * 
	 */


	/**
	 * @param args
	 */

	@Option(name="-train",usage="only  in the train mode, it will use input file to generate model and saved in model.qrf, default: false")
	public boolean train = false;

	@Option(name="-classifier",usage="not use regression model, just use classifier mode, default: false")
	public boolean classifier = false;

	@Option(name="-cpu",usage="number of CPU cores to use, default: 1")
	public int cpu = 1;


	@Option(name="-outputFile",usage="the model predicted value on test dataset, default: null")
	public String outputFile = null;

	@Option(name="-seed",usage="seed for the randomization, default: 12345")
	public Integer seed = 12345;
	
	@Option(name="-kFold",usage="number of cross validation during training step, default: 10")
	public int kFold = 10;

	@Option(name="-numTrees",usage="number of tree for random forest model, default: 1000")
	public Integer numTrees = 1000;
	
	@Option(name="-maxDepth",usage="maximum number of tree depth for random forest model, default: 4")
	public Integer maxDepth = 5;
	
	@Option(name="-maxBins",usage="maximum number of bins used for splitting features at random forest model, default: 100")
	public Integer maxBins = 100;

	@Option(name="-maxMemoryInMB",usage="maximum memory (Mb) to be used for collecting sufficient statistics at random forest model training. larger usually quicker training, default: 10000")
	public Integer maxMemoryInMB = 10000;

	@Option(name="-featureCols",usage="which columns are the features used to predict, allow multiple columns, default: null")
	public ArrayList<Integer> featureColsI = null;

	@Option(name="-strFeatureCols",usage="which columns used to predict are belong to category rather than continuous, allow multiple columns, default: null")
	public ArrayList<Integer> strFeatureColsI = null;

	
	@Option(name="-labelCol",usage="which column is the class to be identified, default: 4")
	public int labelCol = 4;



	@Option(name="-sep",usage="seperate character to split each column, default: \\t")
	public String sep = "\\t";

	@Option(name="-h",usage="show option information")
	public boolean help = false;
	
	final private static String USAGE = "QtlWater [opts] model.qrf inputFile.txt ";

	@Argument
	private List<String> arguments = new ArrayList<String>();

	private TreeSet<Integer> featureCols;
	private TreeSet<Integer> strFeatureCols;
	
	private static Logger log = Logger.getLogger(QtlWater.class);

	private static long startTime = -1;
	
	//private final String featureSubsetStrategy = "auto";
	//private final String impurity = "variance";

	//private final String[] inputHeader = new String[]{"log10p", "dist", "hic", "recomb", "recomb_matched","chromStates","label"};
	//private final String[] inputHeader = new String[]{"log10p", "hic", "recomb","label"};
	//private double[] folds;
	

	
	/**
	 * @param args
	 * @throws Exception 
	 */
	public static void main(String[] args) throws Exception {
		QtlWater qrf = new QtlWater();
		BasicConfigurator.configure();
		Logger.getLogger("org").setLevel(Level.OFF);
	    Logger.getLogger("akka").setLevel(Level.OFF);
	    Logger.getLogger("io.netty").setLevel(Level.OFF);
	    initCloud();
		qrf.doMain(args);
	}
	
	public static void initCloud() {
	    // Setup cloud name
	    String[] args = new String[] { "-name", "QtlWater"};
	    // Build a cloud of 1
	    H2O.main(args);
	    H2O.waitForCloudSize(1, 10*1000 /* ms */);
	  }

	public void doMain(String[] args)
			throws Exception {

					CmdLineParser parser = new CmdLineParser(this);
					//parser.setUsageWidth(80);
					try
					{
						if(help || args.length < 2) throw new CmdLineException(USAGE);
						parser.parseArgument(args);
						
					
					}
					catch (CmdLineException e)
					{
						System.err.println(e.getMessage());
						// print the list of available options
						parser.printUsage(System.err);
						System.err.println();
						return;
					}

					//read input bed file, for each row,
					String modelFile = arguments.get(0);
					String inputFile = arguments.get(1);
					initiate();
					
					
					
					SparkConf sparkConf = new SparkConf().setAppName("QtlWater");
					JavaSparkContext sc = new JavaSparkContext(sparkConf);
					
					//H2OFrame inputData = new H2OFrame(new File(inputFile));
					
					//inputData
					List<StructField> fields = new ArrayList<StructField>();
					for(Integer featureCol : featureCols){
						
						if(strFeatureCols != null && !strFeatureCols.isEmpty()){
							if(strFeatureCols.contains(featureCol)){
								fields.add(DataTypes.createStructField("C" + featureCol, DataTypes.StringType, true));
								continue;
							}
						}
						fields.add(DataTypes.createStructField("C" + featureCol, DataTypes.DoubleType, true));
						
					}
					if(train){
						if(classifier){
							fields.add(DataTypes.createStructField("label", DataTypes.StringType, true));
						}else{
							fields.add(DataTypes.createStructField("label", DataTypes.DoubleType, true));
						}
					}
					
					
					StructType schema = DataTypes.createStructType(fields);
					
						JavaRDD<Row> inputData = sc.textFile(inputFile).map(new Function<String, Row>(){
						
						@Override
						public Row call(String line) throws Exception {
							String[] tmps = line.split(sep);
							Object[] tmpDouble;
							if(train){
								tmpDouble = new Object[featureCols.size() + 1];
								for(Integer featureCol : featureCols){
									if(strFeatureCols != null && !strFeatureCols.isEmpty() && strFeatureCols.contains(featureCol)){
										tmpDouble[featureCol-1] = tmps[featureCol-1];
									}else{
										tmpDouble[featureCol-1] = Double.parseDouble(tmps[featureCol-1]);
									}
									
								}
								if(classifier){
									tmpDouble[featureCols.size()] = tmps[labelCol-1];
								}else{
									tmpDouble[featureCols.size()] = Double.parseDouble(tmps[labelCol-1]);
								}
								
							}else{
								tmpDouble = new Object[featureCols.size()];
								for(Integer featureCol : featureCols){
									if(strFeatureCols != null && !strFeatureCols.isEmpty() && strFeatureCols.contains(featureCol)){
										tmpDouble[featureCol-1] = tmps[featureCol-1];
									}else{
										tmpDouble[featureCol-1] = Double.parseDouble(tmps[featureCol-1]);
									}
									
								}
							}
							//Double[] tmpDouble = new Double[tmps.length];
							//for(int i = 0; i<tmps.length; i++ ){
							//	tmpDouble[i] = Double.parseDouble(tmps[i]);
							//}
							//RowFactory rfact = new RowFactory();
							return RowFactory.create(tmpDouble);
						}
						
					});
					
					SQLContext sqlContext = new SQLContext(sc);

					
					// Prepare training documents, which are labeled.
					H2OContext h2oContext = new H2OContext(sc.sc()).start();
					
					if(train){
						JavaRDD<Row>[] splits = inputData.randomSplit(new double[]{0.9,0.1}, seed);
						
					//	
						
						H2OFrame h2oTraining = h2oContext.toH2OFrame(sc.sc(), sqlContext.createDataFrame(splits[0].rdd(), schema, true));
						H2OFrame h2oValidate = h2oContext.toH2OFrame(sc.sc(), sqlContext.createDataFrame(splits[1].rdd(), schema, true));
						//H2OFrame h2oValidate = h2oContext.asH2OFrame(sqlContext.createDataFrame(splits[1], schema));
						
						
						GBMModel.GBMParameters ggParas = new GBMModel.GBMParameters();
						ggParas._model_id = Key.make("QtlWater_training");
						ggParas._train = h2oTraining._key;
						ggParas._valid = h2oValidate._key;
						ggParas._nfolds = kFold;
						ggParas._response_column = "label";
						ggParas._ntrees = numTrees;
						ggParas._max_depth = maxDepth;
						ggParas._nbins = maxBins;
						
						ggParas._seed = seed;
						
						
						
						GBMModel gbm = new GBM(ggParas).trainModel().get();
						//GBMModel gbm = new GBM(ggParas).computeCrossValidation().get();
						
						
						System.out.println(gbm._output._variable_importances.toString());
						System.out.println(gbm._output._cross_validation_metrics.toString());
						System.out.println(gbm._output._validation_metrics.toString());
						
						System.out.println("output models ...");
						
						if(new File(modelFile).exists())
							org.apache.commons.io.FileUtils.deleteDirectory(new File(modelFile));
						
						List<Key> keysToExport = new LinkedList<Key>();
						keysToExport.add(gbm._key);
						keysToExport.addAll(gbm.getPublishedKeys());

						new ObjectTreeBinarySerializer().save(keysToExport, FileUtils.getURI(modelFile));
						
						//ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(modelFile, true));
						//oos.writeObject(gbm);
						//gbm.writeExternal(oos);
						//oos.close();
						
					}else{
						List<Key> importedKeys = new ObjectTreeBinarySerializer().load(FileUtils.getURI(modelFile));
						GBMModel gbm = (GBMModel) importedKeys.get(0).get();
						
						//ObjectInputStream ois = new ObjectInputStream(new FileInputStream(modelFile));
						
						
						//gbm.readExternal(ois);
						
						//GBMModel gbm = (GBMModel) ois.readObject();
						//ois.close();
						//List<StructField> fieldsInput = new ArrayList<StructField>();
						//fieldsInput.add(DataTypes.createStructField(inputHeader[0], DataTypes.DoubleType, true));
						//fieldsInput.add(DataTypes.createStructField(inputHeader[1], DataTypes.DoubleType, true));
						//fieldsInput.add(DataTypes.createStructField(inputHeader[2], DataTypes.DoubleType, true));
						
						//StructType schemaInput = DataTypes.createStructType(fieldsInput);
						H2OFrame h2oToPredict = h2oContext.toH2OFrame(sc.sc(), sqlContext.createDataFrame(inputData.rdd(), schema, true));

						//H2OFrame h2oPredict = h2oContext.asH2OFrame(gbm.score(h2oToPredict, "predict"));
						H2OFrame h2oPredict = h2oContext.asH2OFrame(h2oToPredict.add(gbm.score(h2oToPredict, "predict")));
						
						if(new File(outputFile + ".tmp").exists())
							org.apache.commons.io.FileUtils.deleteDirectory(new File(outputFile + ".tmp"));

						h2oContext.asDataFrame(h2oPredict, sqlContext).toJavaRDD().map(new Function<Row, String>(){

							@Override
							public String call(Row r) throws Exception {
								String tmp = r.get(0).toString();
								if(tmp.equalsIgnoreCase("null")){
									tmp = "NA";
								}
								for(int i = 1; i< r.size(); i++){
									String t = r.get(i).toString();
									if(t.equalsIgnoreCase("null")){
										t = "NA";
									}
									tmp = tmp + "\t" + t;
								}
								
								return tmp;
							}
							
						}).saveAsTextFile(outputFile + ".tmp");
					
						
						System.out.println("Merging files ...");
					File[] listOfFiles = new File(outputFile + ".tmp").listFiles();
					
					if(new File(outputFile).exists())
						org.apache.commons.io.FileUtils.deleteQuietly(new File(outputFile));
					
					OutputStream output = new BufferedOutputStream(new FileOutputStream(outputFile, true));
		            for (File f : listOfFiles) {
		            	if(f.isFile() && f.getName().startsWith("part-")){
		            		InputStream input = new BufferedInputStream(new FileInputStream(f));
			            	IOUtils.copy(input, output);
			            	IOUtils.closeQuietly(input);
		            	}
		            	
		            }
		            IOUtils.closeQuietly(output);
		            org.apache.commons.io.FileUtils.deleteDirectory(new File(outputFile + ".tmp"));
						//System.err.println(h2oPredict.toString());
						//for(String s : h2oPredict.names())
						//	System.err.println(s);
						//System.err.println(h2oPredict.numCols() + "\t" + h2oPredict.numRows());
						
						//ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(outputFile, true));
						
						//h2oPredict.writeExternal(oos);;
						//oos.close();
					}
					
					finish(inputFile);
	}
	
	private void initiate() throws IOException{
		startTime = System.currentTimeMillis();
		
		
		if(featureColsI == null || featureColsI.isEmpty()){
			featureCols = new TreeSet<Integer>();
			featureCols.add(1);featureCols.add(2);featureCols.add(3);
			//throw new IllegalArgumentException("Need to provide featureCols index: " + featureCols.size());
			
		}else{
			featureCols = new TreeSet<Integer>(featureColsI);
		}
		
		if(strFeatureColsI != null && strFeatureColsI.isEmpty()){
			strFeatureCols = new TreeSet<Integer>(strFeatureColsI);
		}else{
			strFeatureCols = new TreeSet<Integer>();
		}
		
		
		if(labelCol < 1)
			throw new IllegalArgumentException("labelCol index need to be positive: " + labelCol);
		if(featureCols.contains(labelCol))
			throw new IllegalArgumentException("labelCol index exists in training features: " + labelCol);

		
	}

	private void finish(String inputFile) throws IOException{
		File dir = new File("./");
		//if((new File(inputFile)).getParentFile() != null){
		//	dir = new File(inputFile).getParentFile();
		//}else{
		//	dir = new File("./");
		//}
		
		FileFilter fileFilter = new WildcardFileFilter("h2o_*.log*");
		File[] files = dir.listFiles(fileFilter);
		for (int i = 0; i < files.length; i++) {
			org.apache.commons.io.FileUtils.deleteQuietly(files[i]);
		}
		
		long endTime   = System.currentTimeMillis();
		double totalTime = endTime - startTime;
		totalTime /= 1000;
		double totalTimeMins = totalTime/60;
		double totalTimeHours = totalTime/3600;
		
		
		System.out.println("QtlWater's running time is: " + String.format("%.2f",totalTime) + " secs, " + String.format("%.2f",totalTimeMins) +  " mins, " + String.format("%.2f",totalTimeHours) +  " hours");
	}
	
}