/*
 * To the extent possible under law, the ImageJ developers have waived
 * all copyright and related or neighboring rights to this tutorial code.
 *
 * See the Unlicense for details:
 *     https://unlicense.org/
 */

import loci.formats.FormatException;
import loci.plugins.BF;
import net.imagej.ImageJ;
import org.scijava.command.Command;
import org.scijava.plugin.Plugin;

import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.GUI;
import ij.gui.MultiLineLabel;
import ij.gui.OvalRoi;
import ij.gui.Roi;
import ij.measure.ResultsTable;
import ij.plugin.frame.RoiManager;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * A simple plugin for measuring average fluorescence intensities in selected ROIs of a heap of 3D micrographs from Olympus FV software.
 * Format based on "Hello World - a very simple plugin" from ImageJ tutorials.
 * WaitForUserDialogJH based on WaitForUserDialog from IJ
 * <p>
 * Walks through the selected directory and all the subdirectories, finds .oib files, opens them.
 * User first specifies the ROI for measuring average background  fluorescence.
 * Then he should specify at least one ROI to measure the average fluorescence of studied objects (in our case the cell spheroids).
 * Sometimes there are more than one spheroid on the micrograph, so the user can specify more than one ROI.
 * Plugin saves the ROIs.
 * During the next run it computes the average intensities.
 * Quick and dirty, can definitively be improved, but works for us.
 * </p>
 * @author Jaroslav Hanuš
 */
@Plugin(type = Command.class, headless = true, menuPath = "Plugins>Spheroids ROIs intensities")
public class SpheroidsAndLips implements Command {


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
		ij.command().run(SpheroidsAndLips.class, true);
	}

	int bgRoiDefaultX = 50;
	int bgRoiDefaultY = 50;

	int bgRoiDefaultWidth = 350;
	int bgRoiDefaultHeigth = 350;

	int roiDefaultX = 700;
	int roiDefaultY = 700;

	int roiDefaultWidth = 200;
	int roiDefaultHeigth = 200;

	boolean shouldStop = false;
	boolean shouldPrepareROIs = false, shouldRecheckROIs = false, shouldAddAnotherRoi = false;


	@Override
	public void run() {
		final File folder = new File(IJ.getDirectory("Select directory (may contain subdirs) with .oib files to process"));
		WaitForUserDialogJH userDialog = new WaitForUserDialogJH("Going to compute mean gray values from existing ROIs. If you want also prepare ROIs, check corresponding checkboxes", false);
		userDialog.show();
		ResultsTable rt = new ResultsTable();
		rt.addRow();
		ResultsTable bgRt = new ResultsTable();
		bgRt.addRow();
		ResultsTable.getResultsTable().reset();
		if (userDialog.stopPressed()) {
			return;
		} else {
			try {
				processFilesInFolder(folder,bgRt, rt);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		try {
			rt.saveAs(folder.getPath() + "/" + "SpheroidFluorescence_" + folder.getName() + ".xls");
			bgRt.saveAs(folder.getPath() + "/" + "BackgroundFluorescence_" + folder.getName() + ".xls");
		} catch (IOException e) {
			System.out.println("Could not save Results Table!!! " + e);
		}
	}

	public void processFilesInFolder(File folder, ResultsTable bgRt, ResultsTable rt) {
		if (shouldStop) return;
		for (final File fileEntry : Arrays.stream(folder.listFiles()).sorted().collect(Collectors.toList())) {
			if (fileEntry.isDirectory()) {
				processFilesInFolder(fileEntry, bgRt, rt);
			} else {
				if (fileEntry.getName().endsWith(".oib") && !fileEntry.getName().endsWith("After.oib")) {
					System.out.println("Processing " + fileEntry.getName() + "...");
					if (shouldPrepareROIs)
						prepareROIs(fileEntry);
					else
						processFile(fileEntry, bgRt, rt);
				}
			}
		}
	}

	public void prepareROIs(File oibFile) {
		if (shouldStop) return;
		File roiFile = new File(filePathWithoutExtension(oibFile) + ".roi");
		if (roiFile.exists() && (!shouldPrepareROIs || !shouldRecheckROIs))
			return;
		File zipFile = new File(filePathWithoutExtension(oibFile) + ".zip");
		if (zipFile.exists() && (!shouldPrepareROIs || !shouldRecheckROIs))
			return;
		//ImagePlus imp = IJ.openImage(oibFile.getPath());
		ImagePlus imp = null;
		try {
			imp = BF.openImagePlus(oibFile.getPath())[0];
		} catch (FormatException e) {
			throw new RuntimeException(e);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		imp.show();
		imp.setC(1);
		imp.setSlice(2);
		RoiManager rm = new RoiManager(false);
		Roi roi;
		if (zipFile.exists()) {
			rm.open(filePathWithoutExtension(oibFile) + ".zip");
		} else if (roiFile.exists()) {
			rm.open(filePathWithoutExtension(oibFile) + ".roi");
		} else {
			roi = new OvalRoi(bgRoiDefaultX, bgRoiDefaultY,bgRoiDefaultWidth, bgRoiDefaultHeigth);
			rm.addRoi(roi);
		}
		roi = rm.getRoi(0);
		imp.setRoi(roi,true);
		shouldAddAnotherRoi = false;
		WaitForUserDialogJH userBGDialog = new WaitForUserDialogJH("Check proposed background ROI and then hit OK", false);
		userBGDialog.show();
		System.out.println("ROI position: "+roi.getBounds().toString());
		if (userBGDialog.stopPressed()) {
			shouldStop = true;
		} else if (rm.getCount() < 2){
			roi = new OvalRoi(roiDefaultX, roiDefaultY,roiDefaultWidth, roiDefaultHeigth);
			rm.addRoi(roi);
		}
		for (int i = 1; i < rm.getCount(); i++) {
			roi = rm.getRoi(i);
			imp.setRoi(roi,true);
			shouldAddAnotherRoi = false;
			WaitForUserDialogJH userDialog = new WaitForUserDialogJH("Check proposed spheroid ROI and then hit OK", i == rm.getCount() - 1);
			userDialog.show();
			System.out.println("ROI position: "+roi.getBounds().toString());
			if (userDialog.stopPressed()) {
				shouldStop = true;
				break;
			} else 	if (shouldAddAnotherRoi) {
				roi = new OvalRoi(roiDefaultX, roiDefaultY,roiDefaultWidth, roiDefaultHeigth);
				rm.addRoi(roi);
			}
		}

		Roi[] newRois = rm.getRoisAsArray();
		rm.reset();
		int[] selectedIndexes = new int[newRois.length];
		if (!shouldStop) {
			for (int i = 0; i < newRois.length; i++) {
				roi = newRois[i];
				rm.addRoi(roi);
				selectedIndexes[i] = i;
			}
		}
		rm.setSelectedIndexes(selectedIndexes);
		if (rm.getCount() > 1)
			rm.save(filePathWithoutExtension(oibFile) + ".zip");
		else
			rm.save(filePathWithoutExtension(oibFile) + ".roi");
		rm.close();
		imp.close();
	}

	public String filePathWithoutExtension(File oibFile) {
		return oibFile.getAbsolutePath().substring(0, oibFile.getAbsolutePath().lastIndexOf(".oib"));
	}
	public String fileNameWithoutExtension(File oibFile) {
		return oibFile.getName().substring(0, oibFile.getName().lastIndexOf(".oib"));
	}
	public void processFile(File oibFile, ResultsTable bgRt, ResultsTable rt) {
		if (shouldStop) return;
		String fileName = fileNameWithoutExtension(oibFile);
		System.out.println("Adding values from " + fileName + " to table.");
		IJ.run("Set Measurements...", "mean redirect=None decimal=5");

		RoiManager rm = new RoiManager(false);
		File roiFile = new File(filePathWithoutExtension(oibFile) + ".roi");
		File zipFile = new File(filePathWithoutExtension(oibFile) + ".zip");
		if (roiFile.exists()) {
			rm.open(filePathWithoutExtension(oibFile) + ".roi");
		} else if (zipFile.exists()){
			rm.open(filePathWithoutExtension(oibFile) + ".zip");
		} else
			return;

		ImagePlus imp = IJ.openImage(oibFile.getPath());

		String columnName = oibFile.getParentFile().getName() + "-" + fileNameWithoutExtension(oibFile);
		ResultsTable results = rm.multiMeasure(imp);

		//First, we'll process the background ROI (first ROI in the zip)
		String roiName = columnName + "_Bckgd";
		int columnIndex = bgRt.getFreeColumn(roiName);
		bgRt.setValue(columnIndex, 1,fileName + "_Bckgd");
		for (int slice = 0; slice < imp.getNSlices(); slice++) {
			while (bgRt.getCounter() < slice+1) bgRt.addRow();
			double meanInt = results.getValueAsDouble(0, 2*slice);
			int rowIndex = slice+2;
			bgRt.setValue(columnIndex, rowIndex, meanInt);
			System.out.println("Background mean " + meanInt + " from " + fileName + " added to row " + rowIndex + ", column name " + roiName + ", index " + columnIndex + ".");
		}

		//Then, we'll process the spheroids
		for (int i = 1; i < rm.getCount(); i++) {
			roiName = columnName;
			if (i > 1) {
				roiName = columnName + (char)(i+96);
			}
			columnIndex = rt.getFreeColumn(roiName);
			rt.setValue(columnIndex, 1,i > 1 ? fileName + (char)(i+96): fileName);
			for (int slice = 0; slice < imp.getNSlices(); slice++) {
				while (rt.getCounter() < slice+1) rt.addRow();
				double meanInt = results.getValueAsDouble(i, 2*slice);
				int rowIndex = slice+2;
				rt.setValue(columnIndex, rowIndex, meanInt);
				System.out.println("Spheroid mean " + meanInt + " from " + fileName + " added to row " + rowIndex + ", column name " + roiName + ", index " + columnIndex + ".");
			}
		}
		rm.close();
		imp.close();
	}
	class WaitForUserDialogJH extends Dialog implements ActionListener, KeyListener, ItemListener {
		protected Button button, stopButton, addRoiButton;
		protected Checkbox prepareROIsCheckBox, recheckROIsCheckBox;
		protected MultiLineLabel label;
		protected int xloc=-1, yloc=-1;
		private boolean stopPressed;

		public WaitForUserDialogJH(String title, String text, boolean enableAnotherROIAddition) {
			super(IJ.getInstance(), title, false);
			IJ.protectStatusBar(false);
			if (text!=null && text.startsWith("IJ: "))
				text = text.substring(4);
			label = new MultiLineLabel(text, 175);
			if (!IJ.isLinux()) label.setFont(new Font("SansSerif", Font.PLAIN, 14));
			if (IJ.isMacOSX()) {
				RoiManager rm = RoiManager.getInstance();
				if (rm!=null) rm.runCommand("enable interrupts");
			}
			GridBagLayout gridbag = new GridBagLayout(); //set up the layout
			GridBagConstraints c = new GridBagConstraints();
			setLayout(gridbag);
			c.insets = new Insets(6, 6, 0, 6);
			c.gridx = 0; c.gridy = 0; c.anchor = GridBagConstraints.WEST;
			add(label,c);

			button = new Button("  OK  ");
			button.addActionListener(this);
			button.addKeyListener(this);
			c.insets = new Insets(2, 6, 6, 6);
			c.gridx = 0; c.gridy = 3;
			c.anchor = GridBagConstraints.EAST;
			add(button, c);

			stopButton = new Button("  Stop  ");
			stopButton.addActionListener(this);
			stopButton.addKeyListener(this);
			c.gridx = 0; c.gridy = 3;
			c.anchor = GridBagConstraints.WEST;
			add(stopButton, c);

			addRoiButton = new Button("  Add new ROI  ");
			addRoiButton.addActionListener(this);
			addRoiButton.addKeyListener(this);
			c.gridx = 0; c.gridy = 3;
			c.anchor = GridBagConstraints.CENTER;
			addRoiButton.setEnabled(enableAnotherROIAddition);
			add(addRoiButton, c);

			prepareROIsCheckBox = new Checkbox("Prepare ROIs");
			if (shouldPrepareROIs) prepareROIsCheckBox.setState(shouldPrepareROIs);
			prepareROIsCheckBox.addItemListener(this);
			c.gridx = 0; c.gridy = 1;
			c.anchor = GridBagConstraints.CENTER;
			add(prepareROIsCheckBox, c);

			recheckROIsCheckBox = new Checkbox("Check old ROIs");
			if (shouldPrepareROIs) recheckROIsCheckBox.setState(shouldRecheckROIs);
			recheckROIsCheckBox.addItemListener(this);
			c.gridx = 0; c.gridy = 2;
			c.anchor = GridBagConstraints.CENTER;
			add(recheckROIsCheckBox, c);

			setResizable(false);
			addKeyListener(this);
			GUI.scale(this);
			pack();
			if (xloc==-1)
				GUI.centerOnImageJScreen(this);
			else
				setLocation(xloc, yloc);
			setAlwaysOnTop(true);
		}

		public WaitForUserDialogJH(String text) {
			this("Action Required", text, true);
		}

		public WaitForUserDialogJH(String text, boolean enableAnotherROIAddition) {
			this("Action Required", text, enableAnotherROIAddition);
		}

		public void show() {
			super.show();
			synchronized(this) {  //wait for OK
				try {wait();}
				catch(InterruptedException e) {return;}
			}
		}

		public void close() {
			synchronized(this) { notify(); }
			xloc = getLocation().x;
			yloc = getLocation().y;
			dispose();
		}

		public void actionPerformed(ActionEvent e) {
			String s = e.getActionCommand();
			if(s.contains("Stop")){
				stopPressed = true;
			} else if (s.contains("Add")) {
				shouldAddAnotherRoi = true;
			}
			close();
		}

		public void keyPressed(KeyEvent e) {
			int keyCode = e.getKeyCode();
			IJ.setKeyDown(keyCode);
			if (keyCode==KeyEvent.VK_ENTER || keyCode==KeyEvent.VK_ESCAPE) {
				stopPressed = keyCode==KeyEvent.VK_ESCAPE;
				close();
			}
		}

		public boolean stopPressed() {
			return stopPressed;
		}

		public void keyReleased(KeyEvent e) {
			int keyCode = e.getKeyCode();
			IJ.setKeyUp(keyCode);
		}

		public void keyTyped(KeyEvent e) {}

		/** Returns a reference to the 'OK' button */
		public Button getButton() {
			return button;
		}

		/** Display the next WaitForUser dialog at the specified location. */
		public void setNextLocation(int x, int y) {
			xloc = x;
			yloc = y;
		}

		@Override
		public void itemStateChanged(ItemEvent e) {
			shouldPrepareROIs = prepareROIsCheckBox.getState();
			shouldRecheckROIs = recheckROIsCheckBox.getState();
		}
	}
}