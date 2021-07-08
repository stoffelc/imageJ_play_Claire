package ch.epfl.biop.ij2command;

import bdv.util.BdvFunctions;
import ch.epfl.biop.bdv.bioformats.BioFormatsMetaDataHelper;
import ch.epfl.biop.bdv.bioformats.bioformatssource.BioFormatsBdvOpener;
import ch.epfl.biop.bdv.bioformats.command.BioformatsBigdataviewerBridgeDatasetCommand;
import ch.epfl.biop.bdv.bioformats.export.spimdata.BioFormatsConvertFilesToSpimData;
import ch.epfl.biop.omero.imageloader.OmeroToSpimData;
import ch.epfl.biop.omero.omerosource.OmeroSourceOpener;
import mpicbg.spim.data.generic.AbstractSpimData;
import ome.units.quantity.Length;
import ome.units.unit.Unit;
import omero.gateway.Gateway;
import omero.gateway.SecurityContext;
import org.apache.commons.lang.time.StopWatch;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static ch.epfl.biop.ij2command.OmeroTools.getSecurityContext;


@Plugin(type = Command.class,
        menuPath = "Plugins>BIOP>Omero Dataset>Open [Omero Bdv Bridge]",
        description = "description")

public class OpenFilesWithBigDataViewerOmeroBridgeCommand implements Command {

    final private static Logger logger = LoggerFactory.getLogger(OpenFilesWithBigDataViewerOmeroBridgeCommand.class);

    @Parameter(label = "Name of this dataset")
    public String datasetname = "dataset";

    @Parameter(label = "OMERO IDs")
    public String omeroIDs;

    //@Parameter(type = ItemIO.OUTPUT)
    AbstractSpimData spimdata;

    @Parameter(label = "OMERO host")
    String host;

    @Parameter(label = "Enter your gaspar username")
    String username;

    @Parameter(label = "Enter your gaspar password", style = "password", persist = false)
    String password;

    static int port = 4064;



    static public Map<String, Object> getDefaultParameters() {
        Map<String, Object> def = new HashMap();
        def.put("unit", "MILLIMETER");
        def.put("splitrgbchannels",false);
        def.put("positioniscenter","AUTO");
        def.put("switchzandc","AUTO");
        def.put("flippositionx","AUTO");
        def.put("flippositiony","AUTO");
        def.put("flippositionz","AUTO");
        def.put("usebioformatscacheblocksize",true);
        def.put("cachesizex",512);
        def.put("cachesizey",512);
        def.put("cachesizez",1);
        def.put("refframesizeinunitlocation",1);
        def.put("refframesizeinunitvoxsize",1);
        return def;
    }

    // Parameter for dataset creation
    @Parameter(required = false, label="Physical units of the dataset", choices = {"MILLIMETER","MICROMETER","NANOMETER"})
    public String unit = "MILLIMETER";

    @Parameter(required = false, label="Split RGB channels")
    public boolean splitrgbchannels = false;

    @Parameter(required = false, choices = {"AUTO", "TRUE", "FALSE"})
    public String positioniscenter = "AUTO";

    @Parameter(required = false, choices = {"AUTO", "TRUE", "FALSE"})
    public String switchzandc = "AUTO";

    @Parameter(required = false, choices = {"AUTO", "TRUE", "FALSE"})
    public String flippositionx = "AUTO";

    @Parameter(required = false, choices = {"AUTO", "TRUE", "FALSE"})
    public String flippositiony = "AUTO";

    @Parameter(required = false, choices = {"AUTO", "TRUE", "FALSE"})
    public String flippositionz = "AUTO";

    @Parameter(required = false)
    public boolean usebioformatscacheblocksize = true;

    @Parameter(required = false)
    public int cachesizex = 512, cachesizey = 512, cachesizez = 1;

    @Parameter(required = false, label="Reference frame size in unit (position)")
    public double refframesizeinunitlocation = 1;

    @Parameter(required = false, label="Reference frame size in unit (voxel size)")
    public double refframesizeinunitvoxsize = 1;



    public BioFormatsBdvOpener getOpener(String datalocation) {

        Unit bfUnit = BioFormatsMetaDataHelper.getUnitFromString(unit);

        Length positionReferenceFrameLength = new Length(refframesizeinunitlocation, bfUnit);
        Length voxSizeReferenceFrameLength = new Length(refframesizeinunitvoxsize, bfUnit);

        BioFormatsBdvOpener opener = BioFormatsBdvOpener.getOpener()
                .location(datalocation)
                .unit(unit)
                //.auto()
                .ignoreMetadata();

        if (!switchzandc.equals("AUTO")) {
            opener = opener.switchZandC(switchzandc.equals("TRUE"));
        }

        if (!usebioformatscacheblocksize) {
            opener = opener.cacheBlockSize(cachesizex,cachesizey,cachesizez);
        }

        // Not sure it is useful here because the metadata location is handled somewhere else
        if (!positioniscenter.equals("AUTO")) {
            if (positioniscenter.equals("TRUE")) {
                opener = opener.centerPositionConvention();
            } else {
                opener=opener.cornerPositionConvention();
            }
        }

        if (!flippositionx.equals("AUTO")) {
            if (flippositionx.equals("TRUE")) {
                opener = opener.flipPositionX();
            }
        }

        if (!flippositiony.equals("AUTO")) {
            if (flippositiony.equals("TRUE")) {
                opener = opener.flipPositionY();
            }
        }

        if (!flippositionz.equals("AUTO")) {
            if (flippositionz.equals("TRUE")) {
                opener = opener.flipPositionZ();
            }
        }

        opener = opener.unit(unit);

        opener = opener.positionReferenceFrameLength(positionReferenceFrameLength);

        opener = opener.voxSizeReferenceFrameLength(voxSizeReferenceFrameLength);

        if (splitrgbchannels) opener = opener.splitRGBChannels();

        return opener;
    }

    public void run() {
        try{
            List<OmeroSourceOpener> openers = new ArrayList<>();
            String[] omeroIDstrings = omeroIDs.split(",");
            Gateway gateway =  OmeroTools.omeroConnect(host, port, username, password);
            System.out.println( "Session active : "+gateway.isConnected() );
            SecurityContext ctx = OmeroTools.getSecurityContext(gateway);

            for (String s:omeroIDstrings) {
                int ID = Integer.valueOf(s);
                logger.debug("Getting opener for omero ID "+ID);

                //create a new opener and modify it
                OmeroSourceOpener opener = new OmeroSourceOpener()
                        .imageID(ID)
                        .gateway(gateway)
                        .securityContext(ctx)
                        .micrometer()
                        .create();

                openers.add(opener);
            }
            StopWatch watch = new StopWatch();
            logger.debug("All openers obtained, converting to spimdata object ");
            watch.start();
            spimdata = OmeroToSpimData.getSpimData(openers);
            watch.stop();
            logger.debug("Converted to SpimData in "+(int)(watch.getTime()/1000)+" s");
            BdvFunctions.show(spimdata);

        } catch (Exception e) {
            e.printStackTrace();
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
    }

}