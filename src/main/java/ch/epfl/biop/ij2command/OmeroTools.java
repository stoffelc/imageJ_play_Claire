package ch.epfl.biop.ij2command;

import bdv.util.volatiles.VolatileViews;
import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.process.ImageProcessor;
import loci.plugins.LociImporter;
import net.imglib2.Cursor;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.cache.img.CellLoader;
import net.imglib2.cache.img.ReadOnlyCachedCellImgFactory;
import net.imglib2.cache.img.ReadOnlyCachedCellImgOptions;
import net.imglib2.cache.img.SingleCellArrayImg;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import omero.api.RawPixelsStorePrx;
import omero.api.ResolutionDescription;
import omero.gateway.Gateway;
import omero.gateway.LoginCredentials;
import omero.gateway.SecurityContext;
import omero.gateway.facility.BrowseFacility;
import omero.gateway.facility.RawDataFacility;
import omero.gateway.model.ExperimenterData;
import omero.gateway.model.ImageData;
import omero.gateway.model.PixelsData;
import omero.gateway.rnd.Plane2D;
import omero.log.SimpleLogger;
import omero.model.enums.UnitsLength;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.locks.ReentrantLock;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

public class OmeroTools {


    public static Gateway omeroConnect(String hostname, int port, String userName, String password)throws Exception{
        //Omero Connect with credentials and simpleLogger
        LoginCredentials cred = new LoginCredentials();
        cred.getServer().setHost(hostname);
        cred.getServer().setPort(port);
        cred.getUser().setUsername(userName);
        cred.getUser().setPassword(password);
        SimpleLogger simpleLogger = new SimpleLogger();
        Gateway gateway = new Gateway(simpleLogger);
        gateway.connect(cred);
        return gateway;
    }



    public static ImagePlus openImagePlus(String host, String username, String password, long imageID, boolean windowless){
        String options = "";
        options += "location=[OMERO] open=[omero:server=";
        options += host;
        options += "\nuser=";
        options += username;
        options += "\npass=";
        options += password;
        //options += "\ngroupID=";
        //options += groupID;
        options += "\niid=";
        options += imageID;
        options += "]";
        //options += " use_virtual_stack";
        if(windowless) {
            options += " windowless=true ";
        }
        IJ.runPlugIn("loci.plugins.LociImporter",  options);
        LociImporter li = new LociImporter();
        ImagePlus image = WindowManager.getCurrentImage();
        return image;
    }


    public static Collection<ImageData> getImagesFromDataset(Gateway gateway, long DatasetID) throws Exception{
        //List all images contained in a Dataset
        BrowseFacility browse = gateway.getFacility(BrowseFacility.class);
        SecurityContext ctx = getSecurityContext(gateway);
        Collection<Long> datasetIds = new ArrayList<>();
        datasetIds.add(new Long(DatasetID));
        return browse.getImagesForDatasets(ctx, datasetIds);

    }

    public static SecurityContext getSecurityContext(Gateway gateway)throws Exception{
        ExperimenterData exp = gateway.getLoggedInUser();
        long groupID = exp.getGroupId();
        SecurityContext ctx = new SecurityContext(groupID);
        return ctx;
    }

    public static Plane2D getRawPlane(Gateway gateway, long imageID) throws Exception{
        try (RawDataFacility rdf = gateway.getFacility(RawDataFacility.class)) {
            SecurityContext ctx = getSecurityContext(gateway);
            BrowseFacility browse = gateway.getFacility(BrowseFacility.class);
            ImageData image = browse.getImage(ctx, imageID);

            PixelsData pixels = image.getDefaultPixels();
            int sizeZ = pixels.getSizeZ();
            int sizeT = pixels.getSizeT();
            int sizeC = pixels.getSizeC();

            Plane2D p = null;
            for (int z = 0; z < sizeZ; z++) {
                for (int t = 0; t < sizeT; t++) {
                    for (int c = 0; c < sizeC; c++) {
                        p = rdf.getPlane(ctx, pixels, z, t, c);
                    }
                }
            }

            return p;
        }

    }

    public static Plane2D getRawPlane(SecurityContext ctx, RawDataFacility rdf, PixelsData pixels, int z, int t, int c) throws Exception{
            Plane2D p = rdf.getPlane(ctx, pixels, z, t, c);
            return p;
    }

    public static Plane2D getRawPlanefromPixelsData(SecurityContext ctx, RawDataFacility rdf, PixelsData pixels, int z, int t, int c) throws Exception{
            Plane2D p = rdf.getPlane(ctx, pixels, z, t, c);
            return p;
    }

   public static Plane2D getRawTilefromPixelsData(SecurityContext ctx, RawDataFacility rdf, PixelsData pixels, int z, int t, int c, int x, int y, int w, int h) throws Exception{
            Plane2D p = rdf.getTile(ctx, pixels, z, t, c, x, y, w, h);
            return p;
    }

    public static PixelsData getPixelsDataFromOmeroID(long imageID, Gateway gateway, SecurityContext ctx) throws Exception{

            BrowseFacility browse = gateway.getFacility(BrowseFacility.class);
            ImageData image = browse.getImage(ctx, imageID);
            PixelsData pixels = image.getDefaultPixels();
            return pixels;

    }


    public static RandomAccessibleInterval openRandomAccessibleInterval(String host, String username, String password, long imageID, boolean windowless){
        ImagePlus image = OmeroTools.openImagePlus(host,username,password,imageID, windowless);

        long[] total_dim = new long[2];
        total_dim[0] = image.getWidth()*image.getNChannels();
        total_dim[1] = image.getHeight();

        // Create cached image factory of Type Byte
        ReadOnlyCachedCellImgOptions options = new ReadOnlyCachedCellImgOptions();
        // Put cell dimensions to image width and height
        options = options.cellDimensions(image.getWidth(),image.getHeight());
        final ReadOnlyCachedCellImgFactory factory = new ReadOnlyCachedCellImgFactory(options);

        UnsignedShortType t = new UnsignedShortType();

        CellLoader<UnsignedShortType> loader = new CellLoader<UnsignedShortType>(){
            @Override
            public void load(SingleCellArrayImg<UnsignedShortType, ?> singleCellArrayImg) throws Exception {
                long[] positions = new long[2];
                Cursor<UnsignedShortType> cursor = singleCellArrayImg.localizingCursor();
                cursor.localize(positions);
                int index = (int) ((positions[0]+1)/image.getWidth() + 1);
                System.out.println(index);
                ImageProcessor ip = image.getStack().getProcessor(index);
                final long channelOffset = - (index-1)*image.getWidth();

                // move through pixels until there is no pixel left in this cell
                while (cursor.hasNext())
                {
                    // move the cursor forward by one pixel
                    cursor.fwd();
                    //get the current position
                    cursor.localize(positions);
                    long px = positions[0] + channelOffset;
                    long py = positions[1];
                    //get pixel value of the input image (from stack) at pos (px,py) and copy it to the current cell at the same position
                    cursor.get().set(ip.getPixel((int) px,(int) py));
                }
            }

        };

        RandomAccessibleInterval<UnsignedShortType> randomAccessible = factory.create(total_dim, t,loader);
        //ask if pixel has already been loaded or not
        return VolatileViews.wrapAsVolatile(randomAccessible);
    }

    public static RandomAccessibleInterval openRawPlaneRandomAccessibleInterval(SecurityContext ctx, RawDataFacility rdf, PixelsData pixels, boolean windowless) throws Exception {

        int sizeX = pixels.getSizeX();
        int sizeY = pixels.getSizeY();
        int sizeZ = pixels.getSizeZ();
        int sizeC = pixels.getSizeC();
        int sizeT = pixels.getSizeT();
        //pixels.getPixelSizeX(UnitsLength.MILLIMETER);

        long[] total_dim = new long[2];
        total_dim[0] = sizeX*sizeZ*sizeC*sizeT;
        System.out.println("total size : "+sizeZ*sizeC*sizeT);
        total_dim[1] = sizeY;

        // Create cached image factory of Type Byte
        ReadOnlyCachedCellImgOptions options = new ReadOnlyCachedCellImgOptions();
        // Put cell dimensions to image width and height
        options = options.cellDimensions(sizeX,sizeY);
        final ReadOnlyCachedCellImgFactory factory = new ReadOnlyCachedCellImgFactory(options);

        UnsignedShortType type = new UnsignedShortType();


        CellLoader<UnsignedShortType> loader = new CellLoader<UnsignedShortType>(){
            @Override
            public void load(SingleCellArrayImg<UnsignedShortType, ?> singleCellArrayImg) throws Exception {
                long[] positions = new long[2];
                Cursor<UnsignedShortType> cursor = singleCellArrayImg.localizingCursor();
                cursor.localize(positions);

                int index = (int) ((positions[0]+1)/ sizeX + 1);
                //ImageProcessor ip = image.getStack().getProcessor(index);
                final long channelOffset = - (index-1)*sizeX;
                // move through pixels until there is no pixel left in this cell

                int c_index = (index-1) % sizeC;
                int z_index = 0;
                if (sizeZ > 1){
                    z_index = (index-1)/sizeC;
                }
                int t_index = 0;
                if (sizeT > 1) {
                    t_index = (index - 1) / (sizeC * sizeZ);
                }

                Plane2D plane2D = getRawPlane(ctx, rdf, pixels, z_index, t_index, c_index);
                double[][] pixelIntensities = plane2D.getPixelValues();

                while (cursor.hasNext())
                {
                    // move the cursor forward by one pixel
                    cursor.fwd();
                    //get the current position
                    cursor.localize(positions);
                    long px = positions[0]+ channelOffset;
                    long py = positions[1];
                    //get pixel value of the input image (from stack) at pos (px,py) and copy it to the current cell at the same position
                    cursor.get().set((int) pixelIntensities[(int)px][(int)py]);
                }
            }

        };

        RandomAccessibleInterval<UnsignedShortType> randomAccessible = factory.create(total_dim,type,loader);
        //ask if pixel has already been loaded or not
        return VolatileViews.wrapAsVolatile(randomAccessible);
    }


    public static RandomAccessibleInterval openRawRandomAccessibleInterval(SecurityContext ctx, RawDataFacility rdf, PixelsData pixels, int t, int c) throws Exception {
        int sizeX = pixels.getSizeX();
        int sizeY = pixels.getSizeY();
        int sizeZ = pixels.getSizeZ();

        long[] total_dim = new long[3];
        total_dim[0] = sizeX;
        total_dim[1] = sizeY;
        total_dim[2] = sizeZ;

        // Create cached image factory of Type Byte
        ReadOnlyCachedCellImgOptions options = new ReadOnlyCachedCellImgOptions();
        // Put cell dimensions to image width and height
        options = options.cellDimensions(sizeX,sizeY, 1);
        final ReadOnlyCachedCellImgFactory factory = new ReadOnlyCachedCellImgFactory(options);

        UnsignedShortType type = new UnsignedShortType();
        CellLoader<UnsignedShortType> loader = new CellLoader<UnsignedShortType>(){
            @Override
            public void load(SingleCellArrayImg<UnsignedShortType, ?> singleCellArrayImg) throws Exception {
                long[] positions = new long[3];
                Cursor<UnsignedShortType> cursor = singleCellArrayImg.localizingCursor();
                cursor.localize(positions);
                Plane2D plane2D = getRawPlanefromPixelsData(ctx, rdf, pixels, (int) positions[2], t, c);

                double[][] pixelIntensities = plane2D.getPixelValues();

                while (cursor.hasNext())
                {
                    // move the cursor forward by one pixel
                    cursor.fwd();
                    //get the current position
                    cursor.localize(positions);
                    //get pixel value of the input image (from stack) at pos (px,py) and copy it to the current cell at the same position
                    cursor.get().set((int) pixelIntensities[(int)positions[0]][(int)positions[1]]);
                }
            }
        };

        RandomAccessibleInterval<UnsignedShortType> randomAccessible = factory.create(total_dim,type,loader);
        //ask if pixel has already been loaded or not
        return VolatileViews.wrapAsVolatile(randomAccessible);
    }


    public static RandomAccessibleInterval openTiledRawRandomAccessibleInterval(long imageID, int c, int t,int level,SecurityContext ctx, Gateway gateway) throws Exception {
        final PixelsData pixels = OmeroTools.getPixelsDataFromOmeroID(imageID,gateway,ctx);
        //final RawPixelsStorePrx rawPixStore = gateway.getPixelsStore(ctx);
        //rawPixStore.setPixelsId(pixels.getId(), false);
        // set raw pixel to the current resolution level
        //rawPixStore.setResolutionLevel(level);

        int sizeX = pixels.getSizeX();
        int sizeY = pixels.getSizeY();
        int sizeZ = pixels.getSizeZ();

        long[] total_dim = new long[3];

        System.out.println("X " + sizeX);
        System.out.println("Y " + sizeY);
        total_dim[0] = sizeX;
        total_dim[1] = sizeY;
        total_dim[2] = sizeZ;

        // Create cached image factory of Type Byte
        ReadOnlyCachedCellImgOptions options = new ReadOnlyCachedCellImgOptions();
        // Put cell dimensions to arbitrary values
        final int Xdefaultcellsize = 512;
        final int Ydefaultcellsize = 512;

        options = options.cellDimensions(Xdefaultcellsize,Ydefaultcellsize, 1);
        final ReadOnlyCachedCellImgFactory factory = new ReadOnlyCachedCellImgFactory(options);

        UnsignedShortType type = new UnsignedShortType();
        CellLoader<UnsignedShortType> loader = new CellLoader<UnsignedShortType>(){
            @Override
            public void load(SingleCellArrayImg<UnsignedShortType, ?> singleCellArrayImg) {

                long[] positions = new long[3];
                Cursor<UnsignedShortType> cursor = singleCellArrayImg.localizingCursor();
                cursor.localize(positions);

                // +1 since cursor starts just before the block
                int xOffset = (int) positions[0] + 1;
                int yOffset = (int) positions[1];
                //System.out.println("Offset : (" + xOffset + "," + yOffset + ")");
                double[][] pixelIntensities;

                try(RawDataFacility rdf = gateway.getFacility(RawDataFacility.class)) {
                    //synchronized (OmeroTools.class) {
                        int Xcellsize = Xdefaultcellsize;
                        int Ycellsize = Ydefaultcellsize;
                        if (sizeX - xOffset < Xdefaultcellsize){
                            Xcellsize = sizeX - xOffset;
                        }
                        if (sizeY - yOffset < Ydefaultcellsize) {
                            Ycellsize = sizeY - yOffset;
                        }
                        Plane2D plane2D = getRawTilefromPixelsData(ctx, rdf, pixels, (int) positions[2], t, c, xOffset, yOffset, Xcellsize, Ycellsize);
                        pixelIntensities = plane2D.getPixelValues();
                    //}
                    while (cursor.hasNext()) {
                        // move the cursor forward by one pixel
                        cursor.fwd();
                        //get the current position
                        cursor.localize(positions);
                        //get pixel value of the input image (from stack) at pos (px,py) and copy it to the current cell at the same position
                        cursor.get().set((int) pixelIntensities[(int) positions[0] - xOffset][(int) positions[1] - yOffset]);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

            }
        };

        RandomAccessibleInterval<UnsignedShortType> randomAccessible = factory.create(total_dim,type,loader);
        //ask if pixel has already been loaded or not
        return randomAccessible;
    }

}