package ch.epfl.biop.omero.omerosource;

import IceInternal.Ex;
import bdv.util.volatiles.SharedQueue;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.bdv.bioformats.BioFormatsMetaDataHelper;
import ch.epfl.biop.bdv.bioformats.bioformatssource.BioFormatsBdvOpener;
import ch.epfl.biop.bdv.bioformats.bioformatssource.BioFormatsBdvSource;
import ch.epfl.biop.bdv.bioformats.bioformatssource.VolatileBdvSource;
import ch.epfl.biop.ij2command.OmeroTools;

import net.imglib2.FinalInterval;
import net.imglib2.converter.Converter;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.NumericType;
import ome.units.UNITS;
import ome.units.unit.Unit;
import omero.api.RawPixelsStorePrx;
import omero.api.ResolutionDescription;
import omero.gateway.SecurityContext;
import omero.gateway.Gateway;
import omero.gateway.facility.BrowseFacility;
import omero.gateway.model.ImageData;
import omero.gateway.model.PixelsData;
import omero.model.Length;
import omero.model.enums.UnitsLength;
import sc.fiji.bdvpg.sourceandconverter.SourceAndConverterHelper;

import java.util.HashMap;
import java.util.Map;

import static omero.gateway.model.PixelsData.*;

/**
 * Contains parameters that explain how to open all channel sources from an Omero Image
 */
public class OmeroSourceOpener {

    public OmeroSourceOpener() {
    }

    // All serializable fields
    long omeroImageID;
    // Channels options
    boolean splitRGBChannels = false;
    // Unit used for display
    public UnitsLength u;
    // Size of the blocks
    public FinalInterval cacheBlockSize = new FinalInterval(new long[]{0, 0, 0}, new long[]{512, 512, 1}); // needs a default size for z
    // Bioformats location fix
    public double[] positionPreTransformMatrixArray;
    public double[] positionPostTransformMatrixArray;


    // All non-serializable fields
    transient SharedQueue cc = new SharedQueue(2, 4);
    transient Gateway gateway;
    transient SecurityContext securityContext;
    //transient int sizeX, sizeY, sizeZ;
    transient int sizeT;
    transient int sizeC;
    transient int nLevels;
    transient double psizeX;
    transient double psizeY;
    transient double psizeZ;
    transient Map<Integer,int[]> imageSize;
    transient Map<Integer,int[]> tileSize;
    transient long pixelsID;

    // All get methods
    public int getSizeX(int level) { return this.imageSize.get(level)[0]; }
    public int getSizeY(int level) {
        return this.imageSize.get(level)[1];
    }
    public int getSizeZ(int level) {
        return this.imageSize.get(level)[2];
    }
    public int getTileSizeX(int level){ return this.tileSize.get(level)[0]; }
    public int getTileSizeY(int level){ return this.tileSize.get(level)[1]; }
    public int getSizeT() {
        return this.sizeT;
    }
    public int getSizeC() {
        return this.sizeC;
    }
    public int getNLevels() {
        return this.nLevels;
    }
    public long getPixelsID() {
        return this.pixelsID;
    }

    public double getPixelSizeX() {
        return this.psizeX;
    }
    public double getPixelSizeY(){
        return this.psizeY;
    }
    public double getPixelSizeZ(){
        return this.psizeZ;
    }

    //define image ID
    public OmeroSourceOpener imageID(long imageID) {
        this.omeroImageID = imageID;
        return this;
    }

    public OmeroSourceOpener splitRGBChannels() {
        splitRGBChannels = true;
        return this;
    }

    //define unit
    public OmeroSourceOpener unit(UnitsLength u) {
        this.u = u;
        return this;
    }

    public OmeroSourceOpener millimeter() {
        this.u = UnitsLength.MILLIMETER;
        return this;
    }

    public OmeroSourceOpener micrometer() {
        this.u = UnitsLength.MICROMETER;
        return this;
    }

    public OmeroSourceOpener nanometer() {
        this.u = UnitsLength.NANOMETER;
        return this;
    }

    // define gateway
    public OmeroSourceOpener gateway(Gateway gateway) {
        this.gateway = gateway;
        return this;
    }

    // define security context
    public OmeroSourceOpener securityContext(SecurityContext ctx) {
        this.securityContext = ctx;
        return this;
    }

    // define size fields based on omero image ID, gateway and security context
    public OmeroSourceOpener create() throws Exception {
        PixelsData pixels = OmeroTools.getPixelsDataFromOmeroID(omeroImageID, gateway, securityContext);
        RawPixelsStorePrx rawPixStore = gateway.getPixelsStore(securityContext);
        this.pixelsID = pixels.getId();
        rawPixStore.setPixelsId(this.pixelsID, false);
        this.nLevels = rawPixStore.getResolutionLevels();
        this.imageSize = new HashMap<>();
        this.tileSize = new HashMap<>();
        for (int level = 0; level<this.nLevels; level++){
            int[] sizes = new int[3];
            sizes[0] = rawPixStore.getResolutionDescriptions()[level].sizeX;
            sizes[1] = rawPixStore.getResolutionDescriptions()[level].sizeY;
            sizes[2] = pixels.getSizeZ();
            int[] tileSizes = new int[2];
            tileSizes[0] = Math.min(rawPixStore.getTileSize()[0],rawPixStore.getResolutionDescriptions()[rawPixStore.getResolutionLevels()-1].sizeX);
            tileSizes[1] = Math.min(rawPixStore.getTileSize()[1],rawPixStore.getResolutionDescriptions()[rawPixStore.getResolutionLevels()-1].sizeY);
            imageSize.put(level,sizes);
            tileSize.put(level,tileSizes);
        }
        this.sizeT = pixels.getSizeT();
        this.sizeC = pixels.getSizeC();

        this.psizeX = pixels.getPixelSizeX(this.u).getValue();
        this.psizeY = pixels.getPixelSizeY(this.u).getValue();
        //to handle 2D images
        this.psizeZ = 1;
        Length length = pixels.getPixelSizeZ(this.u);
        if(length != null){
            this.psizeZ = length.getValue();
        }
        // must close the rawPixStore to free up resources
        rawPixStore.close();
        return this;
    }


    // All space transformation methods
    public OmeroSourceOpener flipPositionXYZ() {
        if (this.positionPreTransformMatrixArray == null) {
            positionPreTransformMatrixArray = new AffineTransform3D().getRowPackedCopy();
        }
        AffineTransform3D at3D = new AffineTransform3D();
        at3D.set(positionPreTransformMatrixArray);
        at3D.scale(-1);
        positionPreTransformMatrixArray = at3D.getRowPackedCopy();
        return this;
    }

    public OmeroSourceOpener flipPositionX() {
        if (this.positionPreTransformMatrixArray == null) {
            positionPreTransformMatrixArray = new AffineTransform3D().getRowPackedCopy();
        }
        AffineTransform3D at3D = new AffineTransform3D();
        at3D.set(positionPreTransformMatrixArray);
        at3D.scale(-1,1,1);
        positionPreTransformMatrixArray = at3D.getRowPackedCopy();
        return this;
    }

    public OmeroSourceOpener flipPositionY() {
        if (this.positionPreTransformMatrixArray == null) {
            positionPreTransformMatrixArray = new AffineTransform3D().getRowPackedCopy();
        }
        AffineTransform3D at3D = new AffineTransform3D();
        at3D.set(positionPreTransformMatrixArray);
        at3D.scale(1,-1,1);
        positionPreTransformMatrixArray = at3D.getRowPackedCopy();
        return this;
    }

    public OmeroSourceOpener flipPositionZ() {
        if (this.positionPreTransformMatrixArray == null) {
            positionPreTransformMatrixArray = new AffineTransform3D().getRowPackedCopy();
        }
        AffineTransform3D at3D = new AffineTransform3D();
        at3D.set(positionPreTransformMatrixArray);
        at3D.scale(1,1,-1);
        positionPreTransformMatrixArray = at3D.getRowPackedCopy();
        return this;
    }

    //TODO: move getPixelsDataFromOmeroID to OmeroSource opener (as static)
    public OmeroSource<?> createOmeroSource(int channel) throws Exception {
        PixelsData pixels = OmeroTools.getPixelsDataFromOmeroID(omeroImageID, gateway, securityContext);
        OmeroSource source;
        switch(pixels.getPixelType()){
            case FLOAT_TYPE: source = new OmeroSourceFloat(this, channel);
                break;
            case UINT16_TYPE: source = new OmeroSourceUnsignedShort(this, channel);
                break;
            case UINT8_TYPE: source = new OmeroSourceUnsignedByte(this, channel);
                break;
            case UINT32_TYPE: source = new OmeroSourceUnsignedInt(this, channel);
                break;
            default:
                throw new IllegalStateException("Unsupported pixel type : " + pixels.getPixelType());
        }
        return source;
    }

    public SourceAndConverter getSourceAndConvertor(int c) throws Exception {
        OmeroSource concreteSource = createOmeroSource(c);
        VolatileBdvSource volatileSource = new VolatileBdvSource(concreteSource,
                BioFormatsBdvSource.getVolatileOf(concreteSource.getType()),
                cc);

        Converter concreteConverter = SourceAndConverterHelper.createConverter(concreteSource);
        Converter volatileConverter = SourceAndConverterHelper.createConverter(volatileSource);

        return new SourceAndConverter(concreteSource,concreteConverter,
                new SourceAndConverter<>(volatileSource, volatileConverter));

    }
}