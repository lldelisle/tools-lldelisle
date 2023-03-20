/*
 ****************************************************
 * Relative to the generation of the .companion.ome *
 ****************************************************
 * #%L
 * BSD implementations of Bio-Formats readers and writers
 * %%
 * The functions buildXML, makeImage, makePlate, postProcess and asString has been modified and adapted from
 * https://github.com/ome/bioformats/blob/master/components/formats-bsd/test/loci/formats/utests/SPWModelMock.java
 *
 * Copyright (C) 2005 - 2015 Open Microscopy Environment:
 *   - Board of Regents of the University of Wisconsin-Madison
 *   - Glencoe Software, Inc.
 *   - University of Dundee
 *
 * @author Chris Allan <callan at blackcat dot ca>
 * %%
 *
 ****************************************************
 * Relative to the rest of the script *
 ****************************************************
 *
 * * = AUTHOR INFORMATION =
 * Code written by Rémy Dornier, EPFL - SV - PTECH - BIOP
 * and Romain Guiet, EPFL - SV - PTECH - BIOP
 * and Lucille Delisle, EPFL - SV - UPDUB
 * and Pierre Osteil, EPFL - SV - UPDUB
 *
 * Last modification: 2023-03-10
 *
 * = COPYRIGHT =
 * © All rights reserved. ECOLE POLYTECHNIQUE FEDERALE DE LAUSANNE, Switzerland, BioImaging And Optics Platform (BIOP), 2023
 *
 * Licensed under the BSD-3-Clause License:
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided
 * that the following conditions are met:
 *      1. Redistributions of source code must retain the above copyright notice,
 *          this list of conditions and the following disclaimer.
 *      2. Redistributions in binary form must reproduce the above copyright notice,
 *          this list of conditions and the following disclaimer
 *          in the documentation and/or other materials provided with the distribution.
 *      3. Neither the name of the copyright holder nor the names of its contributors
 *          may be used to endorse or promote products
 *          derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 *
 */

/**
 *
 * The purpose of this script is to combine a series of time-lapse images into
 * one file per well/field with possibly multiple channels and multiple time points
 * and in addition create a .companion.ome file to create an OMERO plate object,
 * with a single image per well/field. This .companion.ome file can be directly uploaded on OMERO via
 * OMERO.insight software or CLI, with screen import option.
 *
 * To make the script run
 *  1. Create a parent folder (base_dir) and a output folder (output_dir)
 *  2. Create a dir *Phase, *Green, *Red with the corresponding channels
 *  3. The image names must contains a prefix followed by '_', the name of the well (no 0-pad) followed by '_', followed by the field id followed by '_', the date of the acquisition in YYYYyMMmDDdHHhMMm and the extension '.tif'
 *  4. You must provide the path of the Incucyte XML file to populate key values
 *
 * The expected outputs are:
 *  1. In the output_dir one tiff per well/field (multi-T and potentially multi-C)
 *  2. In the output_dir a .companion.ome
 */

#@ File(style="directory", label="Directory with up to 3 subdirectories ending by Green, Phase and/or Red") base_dir
#@ File(label="Incucyte XML File (plateMap)") incucyteXMLFile
#@ File(style="directory", label="Output directory (must exist)") output_dir
#@ String(label="Final XML file name", value="Test") xmlName
#@ String(label="Number of well in plate", choices={"96", "384"}, value="96") nWells
#@ Integer(label="Maximum number of images per well", value=1, min=1) n_images_per_well
#@ String(label="Objective", choices={"4x","10x","20x"}) objectiveChoice
#@ String(label="Plate name", value="Experiment:0") plateName
#@ String(label="common Key Values formatted as key1=value1;key2=value2", value="") commonKeyValues
#@ Boolean(label="Ignore Compound concentration from plateMap", value=true) ignoreConcentration
#@ Boolean(label="Ignore Cell passage number from plateMap", value=true) ignorePassage
#@ Boolean(label="Ignore Cell seeding concentration from plateMap", value=true) ignoreSeeding
#@ Boolean verbose = true


/**
 * *****************************************************************************************************************
 * ********************************************* Final Variables **************************************************
 * ********************************************* DO NOT MODIFY ****************************************************
 * ****************************************************************************************************************
 */

/** objectives and respective pixel sizes */
objective = 0
objectives = new String[]{"4x", "10x", "20x"}
pixelSizes = new double[]{2.82, 1.24, 0.62}

/** pattern for date */
REGEX_FOR_DATE = ".*_([0-9]{4})y([0-9]{2})m([0-9]{2})d_([0-9]{2})h([0-9]{2})m.tif"

/** Image properties keys */
DIMENSION_ORDER = "dimension_order"
FILE_NAME = "file_name"
IMG_POS_IN_WELL = "img_pos_in_well"
FIRST_ACQUISITION_DATE = "acquisition_date"
FIRST_ACQUISITION_TIME = "acquisition_time"

/** global variable for index to letter conversion */
LETTERS = new String("ABCDEFGHIJKLMNOP")

// Version number = date of last modif
VERSION = "20230310"

/** Key-Value pairs namespace */
GENERAL_ANNOTATION_NAMESPACE = "openmicroscopy.org/omero/client/mapAnnotation"
annotations = new StructuredAnnotations()

/** Plate details and conventions */
PLATE_ID = "Plate:0"
PLATE_NAME = plateName

if (nWells == "96") {
	nRows = 8
	nCols = 12
} else if (nWells == "384") {
	nRows = 16
	nCols = 24
}

WELL_ROWS = new PositiveInteger(nRows)
WELL_COLS = new PositiveInteger(nCols)
WELL_ROW = NamingConvention.LETTER
WELL_COL = NamingConvention.NUMBER

/** XML namespace. */
XML_NS = "http://www.openmicroscopy.org/Schemas/OME/2010-06"

/** XSI namespace. */
XSI_NS = "http://www.w3.org/2001/XMLSchema-instance"

/** XML schema location. */
SCHEMA_LOCATION = "http://www.openmicroscopy.org/Schemas/OME/2010-06/ome.xsd"


/**
 * *****************************************************************************************************************
 * **************************************** Beginning of the script ***********************************************
 * ****************************************************************************************************************
 */

try {

	println "Beginning of the script"

	/**
	* Prepare list of wells name
	*/
	String[] well = []

	well = [(0..(nRows - 1)),(0..(nCols - 1))].combinations().collect{ r,c -> 	LETTERS.substring(r, r + 1) +""+ (c+ 1).toString() }

	if (verbose) println well

	IJ.run("Close All", "")

	// loop for all the wells

	// Store all merged ImagePlus into a HashMap where
	// keys are well name (A1, A10)
	// values are a list of ImagePlus corresponding to different field of view
	Map<String, List<ImagePlus>> wellSamplesMap = new HashMap<>()

	well.each{ input ->
		IJ.run("Close All", "")

		List<ImagePlus> final_imp_list = process_well(base_dir, input, n_images_per_well) //, perform_bc, mediaChangeTime )
		if (!final_imp_list.isEmpty()) {
			wellSamplesMap.put(input, final_imp_list)
			for(ImagePlus final_imp : final_imp_list){
				final_imp.setTitle(input+"_"+final_imp.getProperty(IMG_POS_IN_WELL))
				//final_imp.show()

				def fs = new FileSaver(final_imp)
				File output_path = new File (output_dir ,final_imp.getTitle()+"_merge.tif" )
				fs.saveAsTiff(output_path.toString() )
				final_imp.setProperty(FILE_NAME, output_path.getName())

				IJ.run("Close All", "")
			}
		} else {
			println "No match for " + input
		}
	}


	// get folder and xml file path
	output_dir_abs = output_dir.getAbsolutePath()
	incucyteXMLFilePath = incucyteXMLFile.getAbsolutePath()

	if (! new File(incucyteXMLFilePath).exists()) {
		println "The incucyte file does not exists"
		return
	}

	// select the right objective
	switch (objectiveChoice){
		case "4x":
			objective = 0
			break
		case "10x":
			objective = 1
			break
		case "20x":
			objective = 2
			break
	}

	// get plate scheme as key-values
	Map<String, List<MapPair>> keyValuesPerWell = parseIncucyteXML(incucyteXMLFilePath, ignoreConcentration, ignorePassage, ignoreSeeding)

	// get global key-values
	List<MapPair> globalKeyValues = getGlobalKeyValues(objective, commonKeyValues)
	double pixelSize =  pixelSizes[objective]

	// generate OME-XML metadata file
	OME ome = buildXMLFile(wellSamplesMap, keyValuesPerWell, globalKeyValues, pixelSize)

	// create XML document
	DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance()
	DocumentBuilder parser = factory.newDocumentBuilder()
	Document document = parser.newDocument()

	// Produce a valid OME DOM element hierarchy
	Element root = ome.asXMLElement(document)
	postProcess(root, document)

	// Produce string XML
	try(OutputStream outputStream = new FileOutputStream(output_dir_abs + File.separator + xmlName + ".companion.ome")){
		outputStream.write(asString(document).getBytes())
	} catch(Exception e){
		e.printStackTrace()
	}
	println "End of the script"

} catch (Throwable e) {
	println("Something went wrong: " + e)
	System.err.println("Something went wrong: " + e)

	if (GraphicsEnvironment.isHeadless()){
		// Force to give exit signal of error
		System.exit(1)
	}

}

return

/**
 * ****************************************************************************************************************
 * ******************************************* End of the script **************************************************
 *
 * ****************************************************************************************************************
 *
 * *********************************** Helpers and processing methods *********************************************
 * ***************************************************************************************************************
 */

def process_well(baseDir, input_wellId, n_image_per_well){ //, perform_bc, mediaChangeTime){
	File bf_dir = baseDir.listFiles().find{ it =~ /.*Phase.*/}
	File green_dir = baseDir.listFiles().find{ it =~ /.*Green.*/}
	File red_dir = baseDir.listFiles().find{ it =~ /.*Red.*/}
	//if (verbose) println bf_dir
	//if (verbose) println green_dir

	// The images are stored in a TreeMap where
	// keys are wellSampleId = field identifier
	// values are a TreeMap that we call channelMap where:
	// keys are colors (Green, Grays, Red)
	// values are an ImagePlus (T-stack)
	Map<Integer, Map<String, ImagePlus>> sampleToChannelMap = new TreeMap<>()

	List<File> folder_list = [bf_dir, green_dir, red_dir]
	List<String> channels_list = ["Grays", "Green", "Red"]

	// loop over the field and open images
	for(int wellSampleId = 1; wellSampleId <= n_image_per_well; wellSampleId++) {
		// nT is the number of time-points for the well input_wellId
		int nT = 0
		String first_channel = ""

		// Initiate a channel map for the wellSampleId
		Map<String, ImagePlus> channelMap = new TreeMap<>()

		// Checking if there are images in the corresponding dir
		// which corresponds to the input_wellId
		// and to the wellSampleId
		// The image name should be:
		// Prefix + "_" + input_wellId + "_" + wellSampleId + "_" + year (4 digits) + "y" + month (2 digits) + "m" + day + "d_" + hour + "h" + minute + "m.tif"
		FileFilter fileFilter = new WildcardFileFilter("*_" + input_wellId + "_"+wellSampleId+"_*")
		for(int i = 0; i < folder_list.size(); i++){
			if (folder_list.get(i) != null) {
				File[] files_matching = folder_list.get(i).listFiles(fileFilter as FileFilter)
				if (files_matching.size() != 0) {
					// open the image
					ImagePlus single_channel_imp = FolderOpener.open(folder_list.get(i).getAbsolutePath(), " filter=_"+ input_wellId + "_"+wellSampleId+"_")
					// Phase are 8-bit and need to be changed to 16-bit
					// Other are already 16-bit but it does not hurt
					IJ.run(single_channel_imp, "16-bit", "")

					// check frame size
					if (nT == 0) {
						// This is the first channel with images
						nT = single_channel_imp.getNSlices()
						first_channel = channels_list.get(i)
					} else {
						assert single_channel_imp.getNSlices() == nT : "The number of "+channels_list.get(i)+" images for well "+input_wellId+" and field " + wellSampleId + " does not match the number of images in " + first_channel + "."
					}
					// Get the first date
					// Go to the first time (which is slice)
					single_channel_imp.setSlice(1)
					ImageStack stack = single_channel_imp.getStack()
					int currentSlice = single_channel_imp.getCurrentSlice()
					String label = stack.getShortSliceLabel(currentSlice)
					Pattern date_pattern = Pattern.compile(REGEX_FOR_DATE)
					Matcher date_m = date_pattern.matcher(label)
					if (date_m.matches()) {
						single_channel_imp.setProperty(FIRST_ACQUISITION_DATE, date_m.group(1) + "-" + date_m.group(2) + "-" + date_m.group(3))
						single_channel_imp.setProperty(FIRST_ACQUISITION_TIME, date_m.group(4) + ":" + date_m.group(5) + ":00")
					} else {
						single_channel_imp.setProperty(FIRST_ACQUISITION_DATE, "NA")
						single_channel_imp.setProperty(FIRST_ACQUISITION_TIME, "NA")
					}
					// add the image stack to the channel map for the corresponding color
					channelMap.put(channels_list.get(i), single_channel_imp)
				}
			}
		}
		if (nT != 0) {
			// add the channelmap to the sampleToChannelMap using the wellSampleId as key
			sampleToChannelMap.put(wellSampleId, channelMap)
		}
	}

	ArrayList<ImagePlus> final_imp_list = []

	// Now loop over the wellSampleId which have images
	for(Integer wellSampleId : sampleToChannelMap.keySet()){
		// get the channel map
		Map<String, ImagePlus> channelsMap = sampleToChannelMap.get(wellSampleId)
		ArrayList<String> channels = []
		ArrayList<ImagePlus> current_images = []

		for(String channel : channelsMap.keySet()){
			channels.add(channel)
			current_images.add(channelsMap.get(channel))
		}
		// Get number of time:
		int nT = current_images[0].nSlices

		// Merge all
		ImagePlus merged_imps = Concatenator.run(current_images as ImagePlus[])
		// Re-order to make a multi-channel, time-lapse image
		ImagePlus final_imp = HyperStackConverter.toHyperStack(merged_imps, channels.size() , 1, nT, "xytcz", "Color")
		// add properties to the image
		final_imp.setProperty(DIMENSION_ORDER, DimensionOrder.XYCZT)
		final_imp.setProperty(IMG_POS_IN_WELL, wellSampleId)
		final_imp.setProperty(FIRST_ACQUISITION_DATE, current_images[0].getProperty(FIRST_ACQUISITION_DATE))
		final_imp.setProperty(FIRST_ACQUISITION_TIME, current_images[0].getProperty(FIRST_ACQUISITION_TIME))

		// set LUTs
		(0..channels.size()-1).each{
			final_imp.setC(it + 1)
			IJ.run(final_imp, channels[it], "")
			final_imp.resetDisplayRange()
		}

		final_imp_list.add(final_imp)
	}

	return final_imp_list
}


/**
 * create the full XML metadata (plate, images, channels, annotations....)
 *
 * @param imagesName
 * @param keyValuesPerWell
 * @param globalKeyValues
 * @param pixelSize
 * @return OME-XML metadata instance
 */
def buildXMLFile(Map<String,List<ImagePlus>> wellToImagesMap, Map<String, List<MapPair>> keyValuesPerWell, List<MapPair> globalKeyValues, double pixelSize) {
	// create a new OME-XML metadata instance
	OME ome = new OME()

	Map<String, Integer> imgInWellPosToListMap = new HashMap<>()
	int imgCmp = 0
	for (String wellId: wellToImagesMap.keySet()) {
		// get well position from image name
		List<ImagePlus> imagesWithinWell = wellToImagesMap.get(wellId)

		for (ImagePlus image : imagesWithinWell) {
			// get KVP corresponding to the current well
			// Initiate a list of keyValues for the wellId
			// (or use the existing one)
			List<MapPair> keyValues = []
			if(keyValuesPerWell.containsKey(wellId))
				keyValues = keyValuesPerWell.get(wellId)
			keyValues.addAll(globalKeyValues)

			// create an Image node in the ome-xml
			imgInWellPosToListMap.put(wellId+ "_" +image.getProperty(IMG_POS_IN_WELL),imgCmp)
			ome.addImage(makeImage(imgCmp++, image, keyValues, pixelSize))
		}
	}

	// create Plate node
	ome.addPlate(makePlate(wellToImagesMap, imgInWellPosToListMap, pixelSize, ome))

	// add annotation nodes
	ome.setStructuredAnnotations(annotations)

	return ome
}

/**
 * create an image xml-element, populated with annotations, channel, pixels and path elements
 *
 * @param index image ID
 * @param imageName
 * @param keyValues
 * @param pixelSize
 * @return an image xml-element
 */
def makeImage(int index, ImagePlus imagePlus, List<MapPair> keyValues, double pixelSize) {
	// Create <Image/>
	Image image = new Image()
	image.setID("Image:" + index)
	// The image name is the name of the file without extension
	image.setName(((String)imagePlus.getProperty(FILE_NAME)).split("\\.")[0])
	// Set the acquisitionDate:
	if (imagePlus.getProperty(FIRST_ACQUISITION_DATE) != "NA") {
		image.setAcquisitionDate(new Timestamp(imagePlus.getProperty(FIRST_ACQUISITION_DATE) + "T" + imagePlus.getProperty(FIRST_ACQUISITION_TIME)))
		// Also add it to the key values:
		keyValues.add(new MapPair("acquisition.day", (String)imagePlus.getProperty(FIRST_ACQUISITION_DATE)))
		keyValues.add(new MapPair("acquisition.time", (String)imagePlus.getProperty(FIRST_ACQUISITION_TIME)))
	}
	// Create <MapAnnotations/>
	MapAnnotation mapAnnotation = new MapAnnotation()
	mapAnnotation.setID("ImageKeyValueAnnotation:" + index)
	mapAnnotation.setNamespace(GENERAL_ANNOTATION_NAMESPACE)
	mapAnnotation.setValue(keyValues)
	annotations.addMapAnnotation(mapAnnotation); // add the KeyValues to the general structured annotation element
	image.linkAnnotation(mapAnnotation)

	// Create <Pixels/>
	Pixels pixels = new Pixels()
	pixels.setID("Pixels:" + index)
	pixels.setSizeX(new PositiveInteger(imagePlus.getWidth()))
	pixels.setSizeY(new PositiveInteger(imagePlus.getHeight()))
	pixels.setSizeZ(new PositiveInteger(imagePlus.getNSlices()))
	pixels.setSizeC(new PositiveInteger(imagePlus.getNChannels()))
	pixels.setSizeT(new PositiveInteger(imagePlus.getNFrames()))
	pixels.setDimensionOrder((DimensionOrder) imagePlus.getProperty(DIMENSION_ORDER))
	pixels.setType(getPixelType(imagePlus))
	pixels.setPhysicalSizeX(new Length(pixelSize, UNITS.MICROMETER))
	pixels.setPhysicalSizeY(new Length(pixelSize, UNITS.MICROMETER))

	// Create <TiffData/> under <Pixels/>
	TiffData tiffData = new TiffData()
	tiffData.setFirstC(new NonNegativeInteger(0))
	tiffData.setFirstT(new NonNegativeInteger(0))
	tiffData.setFirstZ(new NonNegativeInteger(0))
	tiffData.setPlaneCount(new NonNegativeInteger(imagePlus.getNSlices()*imagePlus.getNChannels()*imagePlus.getNFrames()))

	// Create <UUID/> under <TiffData/>
	UUID uuid = new UUID()
	uuid.setFileName((String)imagePlus.getProperty(FILE_NAME))
	uuid.setValue(java.util.UUID.randomUUID().toString())
	tiffData.setUUID(uuid)

	// Put <TiffData/> under <Pixels/>
	pixels.addTiffData(tiffData)

	// Create <Channel/> under <Pixels/>
	LUT[] luts = imagePlus.getLuts()
	for (int i = 0; i < luts.length; i++) {
		Channel channel = new Channel()
		channel.setID("Channel:" + i)
		channel.setColor(new Color(luts[i].getRed(255),luts[i].getGreen(255), luts[i].getBlue(255),255))
		pixels.addChannel(channel)
	}

	// Put <Pixels/> under <Image/>
	image.setPixels(pixels)

	return image
}


/**
 * get pixel type based on the imagePlus type
 * @param imp
 * @return pixel type
 */
def getPixelType(ImagePlus imp){
	switch (imp.getType()) {
		case ImagePlus.GRAY8:
			return PixelType.UINT8
		case ImagePlus.GRAY16:
			return PixelType.UINT16
		case ImagePlus.GRAY32:
			return PixelType.FLOAT
		default:
			return PixelType.FLOAT
	}
}

/**
 * create a Plate xml-element, populated with wells and their attributes
 * @param imagesName
 * @return Plate xml-element
 */
def makePlate(Map<String, List<ImagePlus>> wellToImagesMap, Map<String, Integer> imgPosInListMap, double pixelSize, OME ome) {
	// Create <Plate/>
	Plate plate = new Plate()
	plate.setName(PLATE_NAME)
	plate.setID(PLATE_ID)
	plate.setRows(WELL_ROWS)
	plate.setColumns(WELL_COLS)
	plate.setRowNamingConvention(WELL_ROW)
	plate.setColumnNamingConvention(WELL_COL)

	// for each image (one image per well)
	for (String wellId: wellToImagesMap.keySet()) {
		// get well position from image name
		List<ImagePlus> imagesWithinWell = wellToImagesMap.get(wellId)

		// get well position from image name
		int row = convertLetterToNumber(wellId.substring(0, 1))
		int col = Integer.parseInt(wellId.substring(1)) - 1

		// row and col should correspond to a real well
		if(row >= 0 && col >= 0 && col < 12) {
			// Create <Well/> under <Plate/>
			Well well = new Well()
			well.setID(String.format("Well:%d_%d", row, col))
			well.setRow(new NonNegativeInteger(row))
			well.setColumn(new NonNegativeInteger(col))

			for (ImagePlus imagePlus : imagesWithinWell) {
				int wellSampleIndex = imgPosInListMap.get(wellId + "_" + imagePlus.getProperty(IMG_POS_IN_WELL))

				// Create <WellSample/> under <Well/>
				WellSample sample = new WellSample()
				sample.setID(String.format("WellSample:%d", wellSampleIndex))
				sample.setIndex(new NonNegativeInteger(wellSampleIndex))
				if (imagePlus.getCalibration() != null) {
					sample.setPositionX(new Length(imagePlus.getCalibration().xOrigin * pixelSize, UNITS.MICROMETER))
					sample.setPositionY(new Length(imagePlus.getCalibration().yOrigin * pixelSize, UNITS.MICROMETER))
				}
				sample.linkImage(ome.getImage(wellSampleIndex))

				// Put <WellSample/> under <Well/>
				well.addWellSample(sample)
			}

			// Put <Well/> under <Plate/>
			plate.addWell(well)
		}
	}
	return plate
}

/**
 * convert the XML metadata document into string
 *
 * @param document
 * @return
 * @throws TransformerException
 * @throws UnsupportedEncodingException
 */
def asString(Document document) throws TransformerException, UnsupportedEncodingException {
	TransformerFactory transformerFactory = TransformerFactory.newInstance()
	Transformer transformer = transformerFactory.newTransformer()

	//Setup indenting to "pretty print"
	transformer.setOutputProperty(OutputKeys.INDENT, "yes")
	transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4")

	Source source = new DOMSource(document)
	ByteArrayOutputStream os = new ByteArrayOutputStream()
	Result result = new StreamResult(new OutputStreamWriter(os, "utf-8"))
	transformer.transform(source, result)

	return os.toString()
}

/**
 * add document header
 *
 * @param root
 * @param document
 */
def postProcess(Element root, Document document) {
	root.setAttribute("xmlns", XML_NS)
	root.setAttribute("xmlns:xsi", XSI_NS)
	root.setAttribute("xsi:schemaLocation", XML_NS + " " + SCHEMA_LOCATION)
	root.setAttribute("UUID", java.util.UUID.randomUUID().toString())
	document.appendChild(root)
}


/**
 * read Incucyte plate-scheme XML file, extract attributes per well and convert attributes to OMERO-compatible
 * keys-value pairs XML elements.
 *
 * @param path Incucyte plate-scheme XML file path
 * @return Map of OME-XML compatible key-values per well
 */
def parseIncucyteXML(String path, Boolean ignoreConcentration, Boolean ignorePassage, Boolean ignoreSeeding) {
	Map<String, List<MapPair>> keyValuesPerWell = new HashMap<>()

	final String rowAttribute = "row"
	final String columnAttribute = "col"

	final String wellNode = "well"
	final String itemsNode = "items"
	final String wellItemNode = "wellItem"
	final String referenceItemNode = "referenceItem"

	// There are 3 types of referenceItem: Compound, CellType, GrowthCondition
	//
	// For the Compound, each well can have a concentration and a concentrationUnits
	// the referenceItem has a displayName
	// The key should be: displayName (concentrationUnits)
	// The value should be: concentration
	// However, if ignoreConcentration is set to true:
	// The key should be: displayName (NA)
	// The value should be: 1
	//
	// For the CellType, each well can have a passage, a seedingDensity and a seedingDensityUnits
	// the referenceItem has a displayName
	// The passage key should be: displayName_passage
	// The value should be: passage
	// However, if ignorePassage is set to true no key value should be stored for this
	// Then:
	// The seeding key should be: displayName_seedingDensity (seedingDensityUnits)
	// The value should be: seedingDensity
	// However, if ignoreSeeding is set to true:
	// The key should be: displayName
	// The value should be: "yes"
	//
	// For the GrowthCondition, the referenceItem has a displayName
	// The key should be: displayName
	// The value should be: "yes"

	try {
		// create an document
		DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance()
		DocumentBuilder dBuilder = dbFactory.newDocumentBuilder()

		// read the xml file
		Document doc = dBuilder.parse(new File(path))
		doc.getDocumentElement().normalize()

		// get all "well" nodes
		NodeList wellList = doc.getElementsByTagName(wellNode)

		for(int i = 0; i < wellList.getLength(); i++) {
			Node well = wellList.item(i)

			// extract well attributes
			String row = well.getAttributes().getNamedItem(rowAttribute).getTextContent()
			int r = row as int
			String col = well.getAttributes().getNamedItem(columnAttribute).getTextContent()
			String wellNumber = LETTERS.substring(r, r + 1) + (Integer.parseInt(col)+ 1)

			// extract items node, under well node
			Node items = ((Element)well).getElementsByTagName(itemsNode).item(0)
			// get all "wellItem" nodes, under items node
			NodeList wellItemList = ((Element)items).getElementsByTagName(wellItemNode)

			// read referenceItem node's attributes and convert them into key-values
			List<MapPair> keyValues = new ArrayList<>()
			for (int j = 0; j < wellItemList.getLength(); j++) {
				Node wellItem = wellItemList.item(j)
				// extract referenceItem node, under wellItem node
				Node referenceItem = ((Element)wellItem).getElementsByTagName(referenceItemNode).item(0)
				String wellType = wellItem.getAttributes().getNamedItem("type").getTextContent()

				// select the right key-values
				switch (wellType){
					case "Compound":
						String compound_name = referenceItem.getAttributes().getNamedItem("displayName").getTextContent()
						if (ignoreConcentration) {
							keyValues.add(new MapPair(compound_name + " (NA)", "1"))
						} else {
							String unit = wellItem.getAttributes().getNamedItem("concentrationUnits").getTextContent()
							unit = unit.replace("\u00B5", "u")
							String value = wellItem.getAttributes().getNamedItem("concentration").getTextContent()
							keyValues.add(new MapPair(compound_name + " (" + unit + ")", value))
						}
						break
					case "CellType":
						String cell_name = referenceItem.getAttributes().getNamedItem("displayName").getTextContent()
						if (!ignorePassage) {
							String passage = wellItem.getAttributes().getNamedItem("passage").getTextContent()
							keyValues.add(new MapPair(cell_name + "_passage", passage))
						}
						if (ignoreSeeding) {
							keyValues.add(new MapPair(cell_name, "yes"))
						} else {
							String unit = wellItem.getAttributes().getNamedItem("seedingDensityUnits").getTextContent()
							unit = unit.replace("\u00B5", "u")
							String value = wellItem.getAttributes().getNamedItem("seedingDensity").getTextContent()
							keyValues.add(new MapPair(cell_name + "_seedingDensity (" + unit + ")", value))
						}
						break
					case "GrowthCondition":
						String growth_condition = referenceItem.getAttributes().getNamedItem("displayName").getTextContent()
						keyValues.add(new MapPair(growth_condition, "yes"))
						break
				}
			}
			keyValuesPerWell.put(wellNumber,keyValues)
		}
		return keyValuesPerWell
	} catch (Exception e) {
		e.printStackTrace()
		return new HashMap<>()
	}
}

/**
 * make a list of all key-values that are common to all images
 *
 * @param objective
 * @param commonKeyValues (a String with the following format: key1=value1;key2=value2)
 * @return a list of OME-XML key-values
 */
def getGlobalKeyValues(int objective, String commonKeyValues){
	List<MapPair> keyValues = new ArrayList<>()
	keyValues.add(new MapPair("groovy_version", VERSION))
	keyValues.add(new MapPair("objective", objectives[objective]))
	if (commonKeyValues != "") {
		String[] keyValList = commonKeyValues.split(';')
		for (int i = 0; i < keyValList.size(); i ++) {
			String keyval = keyValList[i]
			String[] keyvalsplit = keyval.split('=')
			int nPieces = keyvalsplit.size()
			String value = keyvalsplit[nPieces - 1]
			String key = keyvalsplit[0]
			// In case there are '=' in key
			for (int j = 1; j < nPieces - 1; j++) {
				key += '=' + keyvalsplit[j]
			}
			keyValues.add(new MapPair(key, value))
		}
	}
	return keyValues
}

/**
 * convert alphanumeric well position to numeric position
 *
 * @param letter
 * @return
 */
def convertLetterToNumber(String letter){
	for (int i = 0; i < LETTERS.size(); i++) {
		if (LETTERS.substring(i, i + 1) == letter) {
			return i
		}
	}
	return -1
}


/**
 * *****************************************************************************************************************
 * ************************************************* Imports ****************************************************
 * ****************************************************************************************************************
 */


import ij.IJ
import ij.ImagePlus
import ij.ImageStack
import ij.io.FileSaver
import ij.plugin.Concatenator
import ij.plugin.FolderOpener
import ij.plugin.HyperStackConverter
import ij.process.LUT

import java.awt.GraphicsEnvironment
import java.io.File
import java.util.stream.Collectors
import java.util.stream.IntStream
import java.util.regex.*

import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.Result
import javax.xml.transform.Source
import javax.xml.transform.Transformer
import javax.xml.transform.TransformerException
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

import ome.units.UNITS
import ome.units.quantity.Length

import ome.xml.model.Channel
import ome.xml.model.Image
import ome.xml.model.MapAnnotation
import ome.xml.model.MapPair
import ome.xml.model.OME
import ome.xml.model.Pixels
import ome.xml.model.Plate
import ome.xml.model.StructuredAnnotations
import ome.xml.model.TiffData
import ome.xml.model.UUID
import ome.xml.model.Well
import ome.xml.model.WellSample
import ome.xml.model.enums.DimensionOrder
import ome.xml.model.enums.NamingConvention
import ome.xml.model.enums.PixelType
import ome.xml.model.primitives.Color
import ome.xml.model.primitives.NonNegativeInteger
import ome.xml.model.primitives.PositiveInteger
import ome.xml.model.primitives.Timestamp

import org.apache.commons.io.filefilter.WildcardFileFilter

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.NodeList
