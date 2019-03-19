package edu.lu.uni.serval.violation.code.parser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.gumtreediff.tree.ITree;

import edu.lu.uni.serval.config.Configuration;
import edu.lu.uni.serval.diff.parser.utils.Tokenizer;
import edu.lu.uni.serval.diff.parser.violations.ViolationInstance;
import edu.lu.uni.serval.gumtree.regroup.SimpleTree;
import edu.lu.uni.serval.gumtree.regroup.SimplifyTree;
import edu.lu.uni.serval.utils.FileHelper;

public class ViolationCodeParser {

	private static Logger log = LoggerFactory.getLogger(ViolationCodeParser.class);
	
	public static void main(String[] args) {
		/*
		 * Fixed violations:
		 * 		Violation code files.
		 * 		Position files.
		 */
//		String fixedViolationFilesPath = Configuration.GUM_TREE_INPUT + "prevFiles/";
//		String positionsFilePath = Configuration.GUM_TREE_INPUT + "positions/";
//		int subIndex = 5;
//		String outputPath = Configuration.ROOT_PATH + "Violations_tokens/fixedViolations.list";
//		FileHelper.deleteFile(outputPath);
//		new ViolationCodeParser().parse(fixedViolationFilesPath, positionsFilePath, subIndex, outputPath);
			
		/*
		 * UnFixed violations:
		 * 		Violation code files.
		 * 		Position files.
		 */
		String unfixedViolationFilesPath = Configuration.GUM_TREE_INPUT + "unfixViolations/";
		String un_positionsFilePath = Configuration.GUM_TREE_INPUT + "un_positions/";
		int subIndex2 = 8;
		String outputPath2 = Configuration.ROOT_PATH + "Violations_tokens/unfixedViolations.list";
		FileHelper.deleteFile(outputPath2);
		new ViolationCodeParser().parse(unfixedViolationFilesPath, un_positionsFilePath, subIndex2, outputPath2);
	}

	public void parse(String violationFilesPath, String positionFilesPath, int subIndex, String outputPath) {
		StringBuilder tokensBuilder = new StringBuilder();
		List<File> javaFiles = FileHelper.getAllFilesInCurrentDiectory(violationFilesPath, ".java");
		int counter = 0;
		int a = 0;
		int maxLength = 0;
		for (File javaFile : javaFiles) {
			String fileName = javaFile.getName().replace(".java", ".txt");
			fileName = fileName.substring(subIndex);
			
			//读取文件中的缺陷
			List<ViolationInstance> violations = readViolationInfo(positionFilesPath + fileName);
			
			for (ViolationInstance violation : violations) {
				int startLine = violation.getStartLineNum();
				int endLine = violation.getEndLineNum();
				String violationType = violation.getViolationType();
				
//				if (endLine > startLine + 5) {
//					log.warn("#Large_Violation_Hunk: " + fileName.replace("#", "/").replace(".txt", ".java") + ":" + startLine + ":" + endLine + ":" + violationType);
//					continue;
//				}
				
				ViolationSourceCodeTree violationTree = new ViolationSourceCodeTree(javaFile, startLine, endLine); //在这一步只是创了一个Compilationunit
				violationTree.extract();  //提取到了缺陷代码的AST树
				List<ITree> matchedTrees = violationTree.getViolationSourceCodeTrees();
				if (matchedTrees.size() == 0) {
					System.out.println(fileName + " == " + startLine + " : " + endLine);
					a ++;
					log.warn("#Null_Violation_Hunk: " + fileName.replace("#", "/").replace(".txt", ".java") + ":" + startLine + ":" + endLine + ":" + violationType);
					continue;
				}
				SimpleTree simpleTree = new SimpleTree();
				simpleTree.setLabel("Block");
				simpleTree.setNodeType("Block");
				List<SimpleTree> children = new ArrayList<>();
				
				for (ITree matchedTree : matchedTrees) {
					SimpleTree simpleT = new SimplifyTree().canonicalizeSourceCodeTree(matchedTree, null);
					children.add(simpleT);
				}
				simpleTree.setChildren(children);
				
				String tokens = Tokenizer.getTokensDeepFirst(simpleTree);
				String[] tokensArray = tokens.split(" ");
				int length = tokensArray.length;
				if (length > maxLength) maxLength = length;
				tokensBuilder.append(violationType + ":" + fileName + ":" + violationTree.getViolationFinalStartLine() + ":" + violationTree.getViolationFinalEndLine() + ":" + tokens + "\n");
				counter ++;
				if (counter % 5000 == 0) {
					FileHelper.outputToFile(outputPath, tokensBuilder, true);
					tokensBuilder.setLength(0);
				}
			}
		}

		System.out.println(counter);
		System.out.println(a);
		System.out.println("MaxLength: " + maxLength);
		FileHelper.outputToFile(outputPath, tokensBuilder, true);
		tokensBuilder.setLength(0);
	}
	
	public String sourceCode = "";
	public String tokens = "";
	public String sizes = "";
	
	public void parse(File javaFile, File positionFile) {
		
		List<ViolationInstance> violations = readViolationInfo(positionFile);
		for (ViolationInstance violation : violations) {
			int startLine = violation.getStartLineNum();
			int endLine = violation.getEndLineNum();
			
			String type = violation.getViolationType();
			
//			if (violationTypes != null && violationTypes.contains(type)) {
////				if (endLine > startLine + 5) {
//////					log.warn("#Large_Violation_Hunk: " + javaFile.getName() + ":" + startLine + ":" + endLine + ":" + violationType);
////					continue;
////				}
//			}
			SimpleTree simpleTree = null;
			if ("EQ_DOESNT_OVERRIDE_EQUALS".equals(type)|| "HE_EQUALS_USE_HASHCODE".equals(type) || "HE_INHERITS_EQUALS_USE_HASHCODE".equals(type)||
					"SE_NO_SUITABLE_CONSTRUCTOR".equals(type) || "RI_REDUNDANT_INTERFACES".equals(type)
					||"CN_IDIOM".equals(type)||"SE_NO_SERIALVERSIONID".equals(type) || "SE_COMPARATOR_SHOULD_BE_SERIALIZABLE".equals(type)) {
				ViolationSourceCodeTree parser = new ViolationSourceCodeTree(javaFile, violation.getStartLineNum(), violation.getEndLineNum());
				ITree classNameTree = parser.getClassNameTokens();
				simpleTree = new SimplifyTree().canonicalizeSourceCodeTree(classNameTree, null);
				
				startLine = parser.getViolationFinalStartLine();
				endLine = parser.getViolationFinalEndLine();
			} else {
				ViolationSourceCodeTree violationTree = new ViolationSourceCodeTree(javaFile, startLine, endLine);
				violationTree.extract();
				List<ITree> matchedTrees = violationTree.getViolationSourceCodeTrees();
				if (matchedTrees.size() == 0) {
					System.err.println("#Null_Violation_Hunk: " + javaFile.getName() + ":" + startLine + ":" + endLine);
					continue;
				}
				simpleTree = new SimpleTree();
				simpleTree.setLabel("Block");
				simpleTree.setNodeType("Block");
				List<SimpleTree> children = new ArrayList<>();
				
				for (ITree matchedTree : matchedTrees) {
					SimpleTree simpleT = new SimplifyTree().canonicalizeSourceCodeTree(matchedTree, null);
					children.add(simpleT);
				}
				simpleTree.setChildren(children);
				
				startLine = violationTree.getViolationFinalStartLine();
				endLine = violationTree.getViolationFinalEndLine();
			}
			String tokens = Tokenizer.getTokensDeepFirst(simpleTree);
			String[] tokensArray = tokens.split(" ");
			int length = tokensArray.length;
			sizes += length + "\n";
			this.tokens += tokens + "\n";
			String sourceCode = readSourceCode(javaFile, startLine, endLine, violation.getViolationType());
			this.sourceCode += sourceCode + "\n";
		}
	}

	private String readSourceCode(File javaFile, int startLine, int endLine, String violationType) {
		StringBuilder sourceCode = new StringBuilder("##Source_Code:\n");
		sourceCode.append(violationType).append("\n");
		sourceCode.append(javaFile.getName().replaceAll("#", "/")).append("\nPosition: ").append(startLine).append(" : ").append(endLine).append("\n");
		FileInputStream fis = null;
		Scanner scanner = null;
		
		try {
			fis = new FileInputStream(javaFile);
			scanner = new Scanner(fis);
			int counter = 0;
			while (scanner.hasNextLine()) {
				String line = scanner.nextLine();
				counter ++;
				if (startLine <= counter && counter <= endLine) {
					sourceCode.append(line + "\n");
				}
				if (counter == endLine) break;
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} finally {
			try {
				scanner.close();
				fis.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		return sourceCode.toString();
	}

	private List<ViolationInstance> readViolationInfo(String file) {
		List<ViolationInstance> violations = new ArrayList<>();
		
		String fileContent = FileHelper.readFile(file);
		BufferedReader reader = null;
		reader = new BufferedReader(new StringReader(fileContent));
		String line = null;
		try {
			while ((line = reader.readLine()) != null) {
				String[] positionStr = line.split(":");
				int startLine = Integer.parseInt(positionStr[1]);
				int endLine = Integer.parseInt(positionStr[2]);
				String violationType = positionStr[0];
				
				if (startLine == -1 || endLine == -1) {
					log.warn("#Illegal_Line_Position: " + FileHelper.getFileName(file).replace("#", "/").replace(".txt", ".java") + ":" +  startLine + ":" + endLine + ":" + violationType);
					continue;
				}
				
				ViolationInstance violation = new ViolationInstance(startLine, endLine, violationType);
				violations.add(violation);
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				reader.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return violations;
	}
	
	private List<ViolationInstance> readViolationInfo(File file) {
		List<ViolationInstance> violations = new ArrayList<>();
		
		String fileContent = FileHelper.readFile(file);
		BufferedReader reader = null;
		reader = new BufferedReader(new StringReader(fileContent));
		String line = null;
		try {
			while ((line = reader.readLine()) != null) {
				String[] positionStr = line.split(":");
				int startLine = Integer.parseInt(positionStr[1]);
				int endLine = Integer.parseInt(positionStr[2]);
				String violationType = positionStr[0];
				
				if (startLine == -1 || endLine == -1) {
					continue;
				}
				
				ViolationInstance violation = new ViolationInstance(startLine, endLine, violationType);
				violations.add(violation);
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				reader.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return violations;
	}

	public void setTypes(List<String> violationTypes) {
		this.violationTypes = violationTypes;
	}
	private List<String> violationTypes = null;
	public List<String> getViolationTypes() {
		return this.violationTypes;
	}
}
