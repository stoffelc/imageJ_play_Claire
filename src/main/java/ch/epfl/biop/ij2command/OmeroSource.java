package ch.epfl.biop.ij2command;

import bdv.util.DefaultInterpolators;
import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.view.ExtendedRandomAccessibleInterval;
import net.imglib2.view.Views;
import ome.model.units.BigResult;
import omero.gateway.Gateway;
import omero.gateway.SecurityContext;
import omero.gateway.facility.RawDataFacility;
import omero.gateway.model.PixelsData;
import omero.model.enums.UnitsLength;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

import static ch.epfl.biop.ij2command.ConcurrentUtils.stop;

public class OmeroSource implements Source<UnsignedShortType>{

    protected final DefaultInterpolators< UnsignedShortType > interpolators = new DefaultInterpolators<>();

    int sizeT;
    final int channel_index;
    final long imageID;
    //final Map<Integer,RandomAccessibleInterval<UnsignedShortType>> map = new HashMap<>();
    final Map<Integer,RandomAccessibleInterval<UnsignedShortType>> map = new ConcurrentHashMap<>();
    SecurityContext ctx;
    final Gateway gt;
    double pSizeX;
    double pSizeY;
    double pSizeZ;

    public OmeroSource(long imageID, int c, SecurityContext ctx, Gateway gateway) throws Exception {
        PixelsData pixels = OmeroTools.getPixelsDataFromOmeroID(imageID,gateway,ctx);
        System.out.println("pixel type " + pixels.getPixelType());
        this.imageID = imageID;
        this.sizeT = pixels.getSizeT();
        this.channel_index = c;
        this.gt = gateway;
        this.ctx = ctx;
        this.pSizeX = pixels.getPixelSizeX(UnitsLength.MILLIMETER).getValue();
        System.out.println(this.pSizeX);
        this.pSizeY = pixels.getPixelSizeY(UnitsLength.MILLIMETER).getValue();
        //this.pSizeZ = pixels.getPixelSizeZ(UnitsLength.MILLIMETER).getValue();
        this.pSizeZ = 1;
        System.out.println(this.pSizeY);
        System.out.println(this.pSizeZ);

    }

    @Override
    public boolean isPresent(int t) {
        return t<sizeT;
    }

    @Override
    synchronized public RandomAccessibleInterval<UnsignedShortType> getSource(int t, int level) {
        if (!map.containsKey(t)){
            try {
            map.put(t,OmeroTools.openTiledRawRandomAccessibleInterval(imageID,channel_index,t,ctx,gt));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return map.get(t);
    }

    @Override
    public RealRandomAccessible<UnsignedShortType> getInterpolatedSource(int t, int level, Interpolation method) {
        final UnsignedShortType zero = getType();
        zero.setZero();
        ExtendedRandomAccessibleInterval<UnsignedShortType, RandomAccessibleInterval< UnsignedShortType >>
                eView = Views.extendZero(getSource( t, level ));
        RealRandomAccessible< UnsignedShortType > realRandomAccessible = Views.interpolate( eView, interpolators.get(method) );
        return realRandomAccessible;
    }

    @Override
    public void getSourceTransform(int t, int level, AffineTransform3D transform) {
        //try {
        transform.scale(pSizeX, pSizeY,pSizeZ);
        //} catch (BigResult bigResult) {
       //     bigResult.printStackTrace();
        //}
    }

    @Override
    public UnsignedShortType getType() {
        UnsignedShortType type = new UnsignedShortType();
        return type;
    }

    @Override
    public String getName() {
        return "3D display of my OMERO image";
    }

    @Override
    public VoxelDimensions getVoxelDimensions() {
        return null;
    }

    @Override
    public int getNumMipmapLevels() {
        return 1;
    }

    public int getSizeT(){
        return sizeT;
    }
}