import ij.IJ;
import ij.ImagePlus;
import ij.measure.ResultsTable;
import ij.plugin.ChannelSplitter;
import ij.plugin.ImageCalculator;
import ij.plugin.filter.Analyzer;
import net.imagej.ImageJ;
import org.scijava.command.Command;
import org.scijava.plugin.Plugin;

import java.io.File;
import java.io.IOException;

/**
 * A simple plugin for measuring the surface of areas with fluorescence intensity above chosen threshold in different channels.
 * Format based on "Hello World - a very simple plugin" from ImageJ tutorials.
 * <p>
 * Walks through the selected directory and all the subdirectories, finds .oib files, opens them.
 * Quick and dirty, can definitively be improved, but works for us.
 * </p>
 * @author Jaroslav Hanuš
 */
@Plugin(type = Command.class, headless = true, menuPath = "Plugins>Solvatochromic shift")
public class BlueRedYellowArea implements Command {

	/**
	 * A {@code main()} method for testing.
	 * <p>
	 * When developing a plugin in an Integrated Development Environment (such as
	 * Eclipse or NetBeans), it is most convenient to provide a simple
	 * {@code main()} method that creates an ImageJ context and calls the plugin.
	 * </p>
	 * <p>
	 * In particular, this comes in handy when one needs to debug the plugin:
	 * after setting one or more breakpoints and populating the inputs (e.g. by
	 * calling something like
	 * {@code ij.command().run(MyPlugin.class, "inputImage", myImage)} where
	 * {@code inputImage} is the name of the field specifying the input) debugging
	 * becomes a breeze.
	 * </p>
	 *
	 * @param args unused
	 */
	public static void main(final String... args) {
		// Launch ImageJ as usual.
		final ImageJ ij = new ImageJ();
		ij.launch(args);

		// Launch our "Hello World" command right away.
		ij.command().run(BlueRedYellowArea.class, true);
	}
	public void processFilesInFolder(File folder, ResultsTable rt) {
		for (final File fileEntry : folder.listFiles()) {
			if (fileEntry.isDirectory()) {
				processFilesInFolder(fileEntry, rt);
			} else {
				if (fileEntry.getName().endsWith(".oib")) {
					System.out.println("Processing " + fileEntry.getName() + "...");
					processFileTH(fileEntry, rt);
				}
			}
		}
	}

	public void processFileTH(File oibFile, ResultsTable rt) {
		IJ.run("Set Measurements...", "area_fraction display redirect=None decimal=5");
		rt.addRow();
		rt.addValue("Directory", oibFile.getParentFile().getParentFile().getName() + "/" + oibFile.getParentFile().getName());
		rt.addValue("File", oibFile.getName());

		ResultsTable mainRT = ResultsTable.getResultsTable();
		Integer thresholdBlue = 750;
		Integer thresholdRed = 1000;
		ImagePlus imp = IJ.openImage(oibFile.getPath());
		String fileName = oibFile.getName().substring(0, oibFile.getName().lastIndexOf(".oib"));
		ImagePlus[] channels = ChannelSplitter.split(imp);
		channels[2].close();

		ImagePlus blueCh = channels[0];
		IJ.setAutoThreshold(blueCh, "Default dark");
		IJ.setRawThreshold(blueCh, thresholdBlue, 65535);
		IJ.run(blueCh, "Convert to Mask", "");
		Analyzer analyser = new Analyzer(blueCh);
		analyser.measure();
		int rowNumber = mainRT.getCounter();
		double bluePercentage = mainRT.getValue("%Area", rowNumber-1);
		rt.addValue("%Area Blue Channel", bluePercentage);
		String newName = oibFile.getPath().substring(0, oibFile.getPath().lastIndexOf(".oib")) + "_Blue_TH-" + thresholdBlue + ".jpg";
		IJ.saveAs(blueCh, "Jpeg", newName);

		ImagePlus redCh = channels[1];
		IJ.setAutoThreshold(redCh, "Default dark");
		IJ.setRawThreshold(redCh, thresholdRed, 65535);
		IJ.run(redCh, "Convert to Mask", "");
		analyser = new Analyzer(redCh);
		analyser.measure();
		rowNumber = mainRT.getCounter();
		double redPercentage = mainRT.getValue("%Area", rowNumber-1);
		rt.addValue("%Area Red Channel", redPercentage);
		newName = oibFile.getPath().substring(0, oibFile.getPath().lastIndexOf(".oib")) + "_Red_TH-" + thresholdRed + ".jpg";
		IJ.saveAs(redCh, "Jpeg", newName);

		ImagePlus coloc = ImageCalculator.run(blueCh, redCh, "AND create");
		analyser = new Analyzer(coloc);
		analyser.measure();
		rowNumber = mainRT.getCounter();
		double colocPercentage = mainRT.getValue("%Area", rowNumber-1);
		rt.addValue("%Area Blue AND Red", colocPercentage);
		newName = oibFile.getPath().substring(0, oibFile.getPath().lastIndexOf(".oib")) + "_Coloc-" + thresholdRed + ".jpg";
		IJ.saveAs(coloc, "Jpeg", newName);

		ImagePlus allColors = ImageCalculator.run(blueCh, redCh, "OR create");
		analyser = new Analyzer(allColors);
		analyser.measure();
		rowNumber = mainRT.getCounter();
		double darkPercentage = 100 - mainRT.getValue("%Area", rowNumber-1);
		rt.addValue("%Area Dark", darkPercentage);
		newName = oibFile.getPath().substring(0, oibFile.getPath().lastIndexOf(".oib")) + "_AllColors-" + thresholdRed + ".jpg";
		IJ.saveAs(allColors, "Jpeg", newName);

		rt.addValue("%Area Blue Only", bluePercentage - colocPercentage);
		rt.addValue("%Area Red Only", redPercentage - colocPercentage);
		rt.addValue("%Area D + C + BO + RO", darkPercentage + bluePercentage + redPercentage - colocPercentage);


		//IJ.run("Set Measurements...", "mean display redirect=None decimal=5");
		IJ.run("Set Measurements...", "mean limit redirect=None decimal=5");
		ImagePlus imp2 = IJ.openImage(oibFile.getPath());
		ImagePlus[] channels2 = ChannelSplitter.split(imp2);
		channels2[2].close();

		ImagePlus blueOrig = channels2[0];
		//IJ.run(blueOrig, "8-bit", ""); //Necessary, if I do not find a way how to create 16-bit mask
		//ImagePlus blueMasked = ImageCalculator.run(blueOrig, blueCh , "MIN create");
		IJ.setRawThreshold(blueOrig, Math.round (thresholdBlue/2), 65535);
		analyser = new Analyzer(blueOrig);
		analyser.measure();
		rowNumber = mainRT.getCounter();
		double blueIntDen = mainRT.getValue("Mean", rowNumber-1);
		rt.addValue("Mean Blue", blueIntDen);

//		analyser = new Analyzer(blueCh);
//		analyser.measure();
//		rowNumber = mainRT.getCounter();
//		double blueIntDenMask = mainRT.getValue("RawIntDen", rowNumber-1); //should correspond to number of mask pixels*255
//		rt.addValue("RawIntDen Blue mask", blueIntDenMask);
//		rt.addValue("Blue masked mean", blueIntDen*255/blueIntDenMask);

//		newName = oibFile.getPath().substring(0, oibFile.getPath().lastIndexOf(".oib")) + "_MaskedBlue" + ".jpg";
//		IJ.saveAs(blueMasked, "Jpeg", newName);

		ImagePlus redOrig = channels2[1];
		//IJ.run(redOrig, "8-bit", ""); //Necessary, if I do not find a way how to create 16-bit mask
		//ImagePlus redMasked = ImageCalculator.run(redOrig, redCh, "MIN create");
		IJ.setRawThreshold(redOrig, Math.round (thresholdRed/2), 65535);
		analyser = new Analyzer(redOrig);
		analyser.measure();
		rowNumber = mainRT.getCounter();
		double redIntDen = mainRT.getValue("Mean", rowNumber-1);
		rt.addValue("Mean Red", redIntDen);

//		analyser = new Analyzer(redCh);
//		analyser.measure();
//		rowNumber = mainRT.getCounter();
//		double redIntDenMask = mainRT.getValue("RawIntDen", rowNumber-1); //should correspond to number of mask pixels*255
//		rt.addValue("RawIntDen Red mask", redIntDenMask);
//		rt.addValue("Red masked mean", redIntDen*255/redIntDenMask);

//		newName = oibFile.getPath().substring(0, oibFile.getPath().lastIndexOf(".oib")) + "_MaskedRed" + ".jpg";
//		IJ.saveAs(redMasked, "Jpeg", newName);

		blueCh.close();
		redCh.close();
		coloc.close();
		allColors.close();
		blueOrig.close();
		redOrig.close();

		System.out.println("Adding values from " + fileName + " to table.");
	}

	public void processFileMean(File oibFile, ResultsTable rt) {
		ImagePlus imp = IJ.openImage(oibFile.getPath());
		String fileName = oibFile.getName().substring(0, oibFile.getName().lastIndexOf(".oib"));
		ImagePlus[] channels = ChannelSplitter.split(imp);
		channels[2].close();

		ImagePlus blueCh = channels[0];
		Analyzer analyser = new Analyzer(blueCh, rt);
		analyser.measure();
		rt.addValue("Directory", oibFile.getParentFile().getParentFile().getName() + "/" + oibFile.getParentFile().getName());
		rt.addValue("Channel", "Blue");
		blueCh.close();

		ImagePlus redCh = channels[1];
		analyser = new Analyzer(redCh, rt);
		analyser.measure();
		rt.addValue("Directory", oibFile.getParentFile().getParentFile().getName() + "/" + oibFile.getParentFile().getName());
		rt.addValue("Channel", "Red");
		redCh.close();

		System.out.println("Adding Mean value " + fileName + " to table.");
	}

	public void run() {
		final File folder = new File(IJ.getDirectory("Select directory (may contain subdirs) with .oib files to process"));
		ResultsTable rt = new ResultsTable();
		ResultsTable.getResultsTable().reset();

		processFilesInFolder(folder, rt);
		try {
			rt.saveAs(folder.getPath() + "/" + "Results_" + folder.getName() + ".xls");
		} catch (IOException e) {
			System.out.println("Could not save Results Table!!! " + e);
		}
	}
}