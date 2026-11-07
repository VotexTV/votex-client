package dev.votex.cosmetics;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CompanionMotionTest {
    @Test void gesturesStayBoundedAndContinuousAcrossEveryCycle() {
        float previousLift=CompanionMotion.lift(0,0,0), previousTilt=CompanionMotion.tilt(0,0);
        for(int frame=1;frame<=12000;frame++) {
            float t=frame/120f, lift=CompanionMotion.lift(t,0,0), tilt=CompanionMotion.tilt(t,0);
            assertTrue(Float.isFinite(lift)&&Float.isFinite(tilt));
            assertTrue(Math.abs(lift)<=1.5 && Math.abs(tilt)<=.1);
            assertTrue(Math.abs(lift-previousLift)<.05 && Math.abs(tilt-previousTilt)<.03);
            previousLift=lift;previousTilt=tilt;
        }
    }
    @Test void onlyRegisteredCompanionsReceiveMotion() {
        assertTrue(CompanionMotion.supports("pocket-fox"));
        assertTrue(CompanionMotion.supports("kenney-cube-pets-animal-cat"));
        assertTrue(CompanionMotion.supports("kenney-space-rover"));
        assertFalse(CompanionMotion.supports("custom-decoration"));
    }
}
