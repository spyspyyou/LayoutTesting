package mobile.data.usage.spyspyyou.layouttesting.utils;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import android.util.SparseArray;

import mobile.data.usage.spyspyyou.layouttesting.R;

import static android.graphics.BitmapFactory.decodeResource;

public class BitmapManager {

    private final static int BITMAP_RESOURCES[] = {
            R.drawable.blue_base_tile,
            R.drawable.floor_tile,
            R.drawable.green_base_tile,
            R.drawable.light_bulb_tile_team_blue,
            R.drawable.light_bulb_tile_team_green,
            R.drawable.spawn_tile,
            R.drawable.wall_tile,
            R.drawable.void_tile,
            R.drawable.fluffy,
            R.drawable.slime,
            R.drawable.ghost,
            R.drawable.cross,
            R.drawable.sweet,
            R.drawable.light_bulb_on_square,
            R.drawable.slime_trail
    };

    private static Bitmap defaultBitmap;
    private static boolean loaded = false;

    private static SparseArray<Bitmap> bitmaps = new SparseArray<>();

    public static void loadBitmaps(Resources resources){
        //TODO:the bitmaps should be loaded the size of a tile / their most likely size
        if (loaded){
            Log.i("BitmapManager", "Bitmaps already loaded");
            return;
        }

        Log.i("BitmapManager", "loading");

        loaded = true;

        defaultBitmap = decodeResource(resources, R.drawable.ic_launcher);
        for (int recID:BITMAP_RESOURCES){
            bitmaps.append(recID, BitmapFactory.decodeResource(resources, recID));
        }
    }

    public static void clearMemory(){
        loaded = false;
        bitmaps.removeAtRange(0, bitmaps.size() - 1);
    }

    public static Bitmap getBitmap(int recID){
        return bitmaps.get(recID, defaultBitmap);
    }
}
