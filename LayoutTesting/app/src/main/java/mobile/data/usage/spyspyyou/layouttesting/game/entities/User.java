package mobile.data.usage.spyspyyou.layouttesting.game.entities;

import android.graphics.Canvas;
import android.util.Log;

import mobile.data.usage.spyspyyou.layouttesting.game.Game;
import mobile.data.usage.spyspyyou.layouttesting.game.UserVelocityVector2D;
import mobile.data.usage.spyspyyou.layouttesting.game.events.global.GumShotEvent;
import mobile.data.usage.spyspyyou.layouttesting.ui.views.SurfaceViewGame;
import mobile.data.usage.spyspyyou.layouttesting.utils.Vector2D;

public abstract class User extends Player {

    protected static final int
            MAX_MANA = 1000;
    //int millis
    private static final int
            PARTICLE_COOL_DOWN = 500;

    private static final float
            FLOOR_CHECK_RATIO = 0.75f;
    private long
            lastParticleEjection = 0;
    private final int
            MANA_USAGE;

    private double direction = -Math.PI / 2;

    private final SurfaceViewGame surfaceViewGame;
    protected UserVelocityVector2D velocity;

    protected int mana = 0;

    private boolean falling = false;

    protected User(Vector2D entityPosition, int size, byte characterType, SurfaceViewGame mSurfaceViewGame, int abilityManaUsage) {
        super(entityPosition, characterType);
        surfaceViewGame = mSurfaceViewGame;
        velocity = surfaceViewGame.getUserVelocity();
        MANA_USAGE = abilityManaUsage;
    }


    @Override
    public void update(Game game) {
        if (falling){
            fallingUpdate();
            return;
        }
        //update position + direction
        move();
        //checkFloor(game);
        addMana();
        if (mana > MAX_MANA)mana = MAX_MANA;
    }

    private void move(){
        setDirection(surfaceViewGame.getUserDirection());

        position.add(velocity.getVelocity(slimy));
        //todo:hit box
    }

    private void checkFloor(Game game){
        float tileRad = (1f*getRadius())/SurfaceViewGame.getTileSide();
        Vector2D pointing = new Vector2D((int) (position.x - tileRad * FLOOR_CHECK_RATIO), (int) (position.y - tileRad * FLOOR_CHECK_RATIO));
        String s = "";
        boolean b = true;
        for (; pointing.y < position.y + tileRad * FLOOR_CHECK_RATIO + 1; ++pointing.y){
            for (pointing.x = (int) (position.x - tileRad * FLOOR_CHECK_RATIO); pointing.x < position.x + tileRad * FLOOR_CHECK_RATIO + 1; ++pointing.x){
                s+= "fall check on x" + pointing.x + "y" + pointing.y + '\n';
                if (game.getWorld().isSolid(pointing))b = false;
            }
        }
        Log.i("User", s);
        falling = b;
    }

    private void fallingUpdate(){
        setRadius(--width/2);
    }

    @Override
    public void render(Canvas canvas) {
        super.render(canvas);
        if (!visible) hud.render(canvas);
    }

    public void shootGum(){
        if (lastParticleEjection + PARTICLE_COOL_DOWN < System.currentTimeMillis()){
            lastParticleEjection = System.currentTimeMillis();
            if (!velocity.has0Length())new GumShotEvent(position, velocity).send();
            else new GumShotEvent(position, new Vector2D(Math.cos(direction), Math.sin(direction))).send();
        }
    }

    protected abstract void addMana();

    public boolean activateSkill(Game game){
        if (MANA_USAGE > mana)return false;
        mana -= MANA_USAGE;
        return true;
    }

    @Override
    protected void setDirection(double direction) {
        super.setDirection(direction);
        this.direction = direction;
    }
}
