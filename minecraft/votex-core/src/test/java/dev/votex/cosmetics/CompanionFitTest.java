package dev.votex.cosmetics;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CompanionFitTest {
 @Test void fitsPocketCompanionOutsideHeadAndHeadphoneEnvelope(){
  for(boolean headphones:new boolean[]{false,true}){
   var fit=CompanionFit.of(-7.7f,-2.6953f,-4.6f,1.3f,headphones);
   float edge=-6+(-2.6953f+6)*fit.scale()+fit.x();
   assertTrue(edge<=-4.65f+.00001f);
   if(headphones)assertTrue(-4.6f*fit.scale()+fit.y()>=-3.1501f);
   assertTrue(1.3f*fit.scale()+fit.y()<=-.1499f);
   assertTrue(fit.scale()>0&&fit.scale()<=1);
  }
 }
 @Test void preservesAlreadyClearCompanionsAndBoundsInvalidInputs(){
  assertEquals(CompanionFit.IDENTITY,CompanionFit.of(-9,-5,-3,-1,false));
  assertEquals(CompanionFit.IDENTITY,CompanionFit.of(Float.NaN,2,0,3,true));
 }
}
