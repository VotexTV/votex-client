package dev.votex.arcade;
import java.util.*;
/** Small deterministic games; independent of Minecraft and server state. All worlds/boards are bounded. */
public final class ArcadeGames {
 private ArcadeGames(){}
 public static final class Pong {
  public double ballX=.5,ballY=.5,vx=.6,vy=.2,left=.5,right=.5,wait=.65;public int playerScore,aiScore;public boolean over;
  public void reset(){ballX=ballY=left=right=.5;vx=.6;vy=.2;wait=.65;playerScore=aiScore=0;over=false;}
  public void update(double dt,double playerTarget){if(over)return;dt=Math.clamp(dt,0,.05);left=Math.clamp(playerTarget,.1,.9);double target=ballX>.45&&vx>0?ballY:.5;right=Math.clamp(right+Math.clamp(target-right,-.47*dt,.47*dt),.1,.9);if(wait>0){wait-=dt;return;}double previous=ballX;ballX+=vx*dt;ballY+=vy*dt;if(ballY<.015){ballY=.015;vy=Math.abs(vy);}if(ballY>.985){ballY=.985;vy=-Math.abs(vy);}if(vx<0&&previous>=.073&&ballX<.073&&Math.abs(ballY-left)<=.115){ballX=.073;bounce(left,1);}if(vx>0&&previous<=.927&&ballX>.927&&Math.abs(ballY-right)<=.115){ballX=.927;bounce(right,-1);}if(ballX<-.03||ballX>1.03){boolean player=ballX>1;if(player)playerScore++;else aiScore++;over=playerScore>=7||aiScore>=7;ballX=ballY=.5;vx=player?-.6:.6;vy=(playerScore+aiScore)%2==0?.2:-.2;wait=.65;}}
  private void bounce(double paddle,int direction){double speed=Math.min(1.25,Math.hypot(vx,vy)*1.055);double angle=Math.clamp((ballY-paddle)/.11,-1,1)*1.05;vx=Math.cos(angle)*speed*direction;vy=Math.sin(angle)*speed;}
 }
 public static final class TicTacToe {
  private static final int[][] WIN_LINES={{0,1,2},{3,4,5},{6,7,8},{0,3,6},{1,4,7},{2,5,8},{0,4,8},{2,4,6}};
  public final int[] board=new int[9];public boolean ai=true;public int turn=1,winner;
  public void reset(){Arrays.fill(board,0);turn=1;winner=0;}
  public boolean play(int cell){if(cell<0||cell>8||winner!=0||board[cell]!=0)return false;board[cell]=turn;winner=result(board);if(winner!=0)return true;turn=3-turn;if(ai&&turn==2){int best=-100,move=-1;for(int i=0;i<9;i++)if(board[i]==0){board[i]=2;int value=minimax(1,0);board[i]=0;if(value>best){best=value;move=i;}}if(move>=0)board[move]=2;winner=result(board);turn=1;}return true;}
  private int minimax(int player,int depth){int result=result(board);if(result!=0)return result==2?10-depth:result==1?depth-10:0;int best=player==2?-100:100;for(int i=0;i<9;i++)if(board[i]==0){board[i]=player;int v=minimax(3-player,depth+1);board[i]=0;best=player==2?Math.max(best,v):Math.min(best,v);}return best;}
  public static int result(int[] board){for(var line:WIN_LINES)if(board[line[0]]!=0&&board[line[0]]==board[line[1]]&&board[line[0]]==board[line[2]])return board[line[0]];for(int value:board)if(value==0)return 0;return 3;}
 }
 public static final class Flappy {
  public static final int PIPE_COUNT=4;public final double[] pipes=new double[PIPE_COUNT],gaps=new double[PIPE_COUNT];public final boolean[] scored=new boolean[PIPE_COUNT];public double bird=.45,velocity;public int score;public boolean started,over;private final Random random=new Random(0x564f544558L);
  public Flappy(){reset();}
  public void reset(){bird=.45;velocity=0;score=0;started=over=false;for(int i=0;i<PIPE_COUNT;i++){pipes[i]=1.1+i*.56;gaps[i]=.25+random.nextDouble()*.5;scored[i]=false;}}
  public void flap(){if(over)return;started=true;velocity=-.39;}
  public void update(double dt){if(!started||over)return;dt=Math.clamp(dt,0,.05);velocity+=.95*dt;bird+=velocity*dt;if(bird<.025||bird>.975){over=true;return;}for(int i=0;i<PIPE_COUNT;i++){pipes[i]-=.28*dt;if(pipes[i]<-.12){double last=0;for(double px:pipes)last=Math.max(last,px);pipes[i]=last+.56;gaps[i]=.25+random.nextDouble()*.5;scored[i]=false;}if(!scored[i]&&pipes[i]+.09<.24){scored[i]=true;score++;}if(.24+.022>pipes[i]&&.24-.022<pipes[i]+.09&&(bird-.022<gaps[i]-.16||bird+.022>gaps[i]+.16)){over=true;return;}}}
 }
 public static final class Tetris {
  public static final int COLS=10,ROWS=20;private static final int[][] SHAPES={{4,5,6,7},{1,2,5,6},{1,4,5,6},{1,2,4,5},{0,1,5,6},{0,4,5,6},{2,4,5,6}};
  public final int[][] board=new int[ROWS][COLS];public int type,next,rotation,x=3,y=-1,score,lines;public boolean over;private double fall;private final Random random=new Random(0x414352414445L);private final int[] bag=new int[7];private int bagIndex=7;
  public Tetris(){reset();}
  public void reset(){for(var row:board)Arrays.fill(row,0);score=lines=rotation=0;over=false;fall=0;bagIndex=7;next=take();spawn();}
  private int take(){if(bagIndex==7){for(int i=0;i<7;i++)bag[i]=i;for(int i=6;i>0;i--){int j=random.nextInt(i+1),old=bag[i];bag[i]=bag[j];bag[j]=old;}bagIndex=0;}return bag[bagIndex++];}
  private void spawn(){type=next;next=take();rotation=0;x=3;y=-1;fall=0;if(!fits(x,y,rotation))over=true;}
  public int cellX(int index,int rot){int value=SHAPES[type][index],cx=value%4,cy=value/4;if(type==1)return cx;for(int i=0;i<rot;i++){int old=cx;cx=(type==0?3:2)-cy;cy=old;}return cx;}
  public int cellY(int index,int rot){int value=SHAPES[type][index],cx=value%4,cy=value/4;if(type==1)return cy;for(int i=0;i<rot;i++){int old=cx;cx=(type==0?3:2)-cy;cy=old;}return cy;}
  private boolean fits(int px,int py,int rot){for(int i=0;i<4;i++){int cx=px+cellX(i,rot),cy=py+cellY(i,rot);if(cx<0||cx>=COLS||cy>=ROWS||(cy>=0&&board[cy][cx]!=0))return false;}return true;}
  public boolean move(int delta){if(!over&&fits(x+delta,y,rotation)){x+=delta;return true;}return false;}
  public void rotate(){if(over)return;int rot=(rotation+1)%4;int[] kicks={0,-1,1,-2,2};for(int dy=0;dy>=-1;dy--)for(int dx:kicks)if(fits(x+dx,y+dy,rot)){x+=dx;y+=dy;rotation=rot;return;}}
  public void update(double dt,boolean soft){if(over)return;fall+=Math.clamp(dt,0,.05);double interval=soft?.04:Math.max(.10,.72-lines/10*.065);int steps=0;while(fall>=interval&&steps++<3&&!over){fall-=interval;if(fits(x,y+1,rotation)){y++;if(soft)score++;}else lock();}}
  public int ghostY(){int end=y;while(fits(x,end+1,rotation))end++;return end;}
  public void hardDrop(){if(over)return;int end=ghostY();score+=(end-y)*2;y=end;lock();}
  private void lock(){for(int i=0;i<4;i++){int cy=y+cellY(i,rotation);if(cy<0){over=true;return;}board[cy][x+cellX(i,rotation)]=type+1;}int removed=clearLines();score+=new int[]{0,100,300,500,800}[removed]*(1+lines/10);lines+=removed;spawn();}
  public int clearLines(){int removed=0;for(int row=ROWS-1;row>=0;row--){boolean full=true;for(int cell:board[row])if(cell==0){full=false;break;}if(full){for(int r=row;r>0;r--)System.arraycopy(board[r-1],0,board[r],0,COLS);Arrays.fill(board[0],0);removed++;row++;}}return removed;}
 }
}
