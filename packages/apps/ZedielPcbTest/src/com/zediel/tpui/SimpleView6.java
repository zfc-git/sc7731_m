package com.zediel.tpui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.view.Gravity;
import android.util.DisplayMetrics;
import java.util.ArrayList;
import com.zediel.itemstest.TpTest;

public class SimpleView6 extends View {

    private int mov_x;//声明起点坐标
    private int mov_y;

    private float down_x;
    private float down_y;
    private float up_x;
    private float up_y;

    private Paint paintRed;//声明画笔

    private Paint paint;
    //private int crossCount = 25;
    //private int verticalCount = 15;
    private int crossCount = 13;
    private int verticalCount = 5;
    private float gap = 2.0f;
    float rectWidth;
    float rectHeight;
    int totalCount;

    Context mContext;
    boolean[] states;

    private Canvas canvas;//画布
    private Bitmap bitmap;//位图
    private int blcolor;


    private  int LCM_WIDTH = 1280;  //800*1280    1920*1080
    private  int LCM_HEIGHT = 800;

    public SimpleView6(Context context) {
        super(context);
        mContext = context;
        LCM_WIDTH = TpTest.LCM_W ;
        LCM_HEIGHT = TpTest.LCM_H ;

        bitmap = Bitmap.createBitmap(LCM_WIDTH, LCM_HEIGHT, Bitmap.Config.ARGB_8888); //设置位图的宽高
        paintRed=new Paint(Paint.DITHER_FLAG);//创建一个画笔
        canvas=new Canvas();
        canvas.setBitmap(bitmap);


        paintRed.setStyle(Paint.Style.STROKE);//设置非填充
        paintRed.setStrokeWidth(5);//笔宽5像素
        paintRed.setColor(Color.RED);//设置为红笔
        paintRed.setAntiAlias(true);//锯齿不显示

        paint = new Paint();
        paint.setStyle(Paint.Style.FILL);//设置非填充
        paint.setStrokeWidth(1);//笔宽5像素
        paint.setColor(Color.GRAY);//设置为红笔
        paint.setAntiAlias(true);//锯齿不显示

        rectWidth = (LCM_WIDTH-(crossCount+1)*gap)/crossCount;
        rectHeight = (LCM_HEIGHT-(verticalCount+1)*gap)/(verticalCount+2);

        totalCount = crossCount*3+verticalCount*3-3;
	//	  totalCount = crossCount*3+verticalCount*3-3+(crossCount-3)*2;
        Log.e("lqh","rectWidth="+rectWidth+"  rectHeight="+rectHeight+" verticalCount="+verticalCount+" totalCount="+totalCount);
        states = new boolean[totalCount];
        for(int i=0; i<totalCount; i++){
            states[i]=false;
        }
    }

    public float getTileWidth () {
        return rectWidth;
    }

    public float getTileHeight () {
        return rectHeight;
    }


    //画位图
    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawBitmap(bitmap,0,0,null);


        for(int i = 0; i < totalCount; i++){
            if(states[i]){
                paint.setColor(Color.GREEN);
            }else{
                paint.setColor(Color.GRAY);
            }
            if(i<crossCount){
                canvas.drawRect(i*rectWidth+(i+1)*gap, gap,(i+1)*rectWidth+(i+1)*gap, rectHeight+gap,paint);

            }else if(i<crossCount+verticalCount){
                int index = i-crossCount;
                canvas.drawRect(gap, (gap+rectHeight)+index*rectHeight+(index+1)*gap,rectWidth+gap,
                        (gap+rectHeight)+(index+1)*rectHeight+(index+1)*gap,paint);
            }else if(i<crossCount+verticalCount*2){
                int index = i-crossCount-verticalCount;
                canvas.drawRect(gap+crossCount/2*(gap+rectWidth), (gap+rectHeight)+index*rectHeight+(index+1)*gap,
                        gap+crossCount/2*(gap+rectWidth)+rectWidth, (gap+rectHeight)+(index+1)*rectHeight+(index+1)*gap,paint);
            }else if(i<crossCount+verticalCount*3){
                int index = i-crossCount-verticalCount*2;
                canvas.drawRect(LCM_WIDTH-(gap+rectWidth), (gap+rectHeight)+index*rectHeight+(index+1)*gap,LCM_WIDTH-gap,
                        (gap+rectHeight)+(index+1)*rectHeight+(index+1)*gap,paint);
            }else if(i<crossCount+verticalCount*3+crossCount/2-1){
                int index = i-crossCount-verticalCount*3;
                canvas.drawRect(gap+rectWidth+index*rectWidth+(index+1)*gap, gap+(verticalCount/2+1)*(rectHeight+gap),
                        gap+rectWidth+(index+1)*rectWidth+(index+1)*gap,gap+(verticalCount/2+1)*(rectHeight+gap)+rectHeight,paint);
            }else if(i<crossCount+verticalCount*3+crossCount/2-1+crossCount/2-1){
                int index = i-crossCount-verticalCount*3-crossCount/2+1;
                canvas.drawRect((crossCount/2+1)*(gap+rectWidth)+index*rectWidth+(index+1)*gap, gap+(verticalCount/2+1)*(rectHeight+gap),
                        (crossCount/2+1)*(gap+rectWidth)+(index+1)*rectWidth+(index+1)*gap,gap+(verticalCount/2+1)*(rectHeight+gap)+rectHeight,paint);
            }else if(i<crossCount+verticalCount*3+crossCount/2-1+crossCount/2-1+crossCount){
                int index = i-crossCount-verticalCount*3-crossCount/2+1-crossCount/2+1;
                canvas.drawRect(index*rectWidth+(index+1)*gap, (verticalCount+1)*(rectHeight+gap)+gap,
                        (index+1)*rectWidth+(index+1)*gap,(verticalCount+1)*(rectHeight+gap)+rectHeight+gap,paint);

            }else if(i<crossCount+verticalCount*3+crossCount/2-1+crossCount/2-1+crossCount+(crossCount-3)/2){
                int index = i-crossCount-verticalCount*3-crossCount/2+1-crossCount/2+1-crossCount;
                canvas.drawRect(index*rectWidth+(index+1)*gap+gap+rectWidth, ((verticalCount+2)/4)*(rectHeight+gap)+gap,
                        (index+1)*rectWidth+(index+1)*gap+gap+rectWidth,((verticalCount+2)/4)*(rectHeight+gap)+rectHeight+gap,paint);
            }else if(i<crossCount+verticalCount*3+crossCount/2-1+crossCount/2-1+crossCount+(crossCount-3)/2+(crossCount-3)/2){
                int index = i-crossCount-verticalCount*3-crossCount/2+1-crossCount/2+1-crossCount-(crossCount-3)/2;
                canvas.drawRect((crossCount/2+1)*(gap+rectWidth)+index*rectWidth+(index+1)*gap, (verticalCount+2)/4*(rectHeight+gap)+gap,
                        (crossCount/2+1)*(gap+rectWidth)+(index+1)*rectWidth+(index+1)*gap,((verticalCount+2)/4)*(rectHeight+gap)+rectHeight+gap,paint);
            }else if(i<crossCount+verticalCount*3+crossCount/2-1+crossCount/2-1+crossCount+(crossCount-3)/2+(crossCount-3)/2+(crossCount-3)/2){
                int index = i-crossCount-verticalCount*3-crossCount/2+1-crossCount/2+1-crossCount-(crossCount-3)/2-(crossCount-3)/2;
                canvas.drawRect(index*rectWidth+(index+1)*gap+gap+rectWidth, ((verticalCount+2)/4*3)*(rectHeight+gap)+gap,
                        (index+1)*rectWidth+(index+1)*gap+gap+rectWidth,((verticalCount+2)/4*3)*(rectHeight+gap)+rectHeight+gap,paint);
            }else if(i<crossCount+verticalCount*3+crossCount/2-1+crossCount/2-1+crossCount+(crossCount-3)/2+(crossCount-3)/2+(crossCount-3)/2+(crossCount-3)/2){
                int index = i-crossCount-verticalCount*3-crossCount/2+1-crossCount/2+1-crossCount-(crossCount-3)/2-(crossCount-3)/2-(crossCount-3)/2;
                canvas.drawRect((crossCount/2+1)*(gap+rectWidth)+index*rectWidth+(index+1)*gap, ((verticalCount+2)/4*3)*(rectHeight+gap)+gap,
                        (crossCount/2+1)*(gap+rectWidth)+(index+1)*rectWidth+(index+1)*gap,((verticalCount+2)/4*3)*(rectHeight+gap)+rectHeight+gap,paint);
            }

        }


            for(int i = 0; i < totalCount; i++){
                if(i<crossCount){
                    arrayList.add(i,new PointInfo(i*rectWidth+(i+1)*gap,gap,((i+1)*rectWidth+(i+1)*gap),(rectHeight+gap)));
                }else if(i<crossCount+verticalCount){
                    int index = i-crossCount;
                    arrayList.add(i,new PointInfo(gap,(gap+rectHeight)+index*rectHeight+(index+1)*gap,(rectWidth+gap),(gap+rectHeight)+(index+1)*rectHeight+(index+1)*gap));
                }else if(i<crossCount+verticalCount*2){
                    int index = i-crossCount-verticalCount;
                    arrayList.add(i,new PointInfo(gap+crossCount/2*(gap+rectWidth),(gap+rectHeight)+index*rectHeight+(index+1)*gap,(gap+crossCount/2*(gap+rectWidth)+rectWidth),((gap+rectHeight)+(index+1)*rectHeight+(index+1)*gap)));
                }else if(i<crossCount+verticalCount*3){
                    int index = i-crossCount-verticalCount*2;
                    arrayList.add(i,new PointInfo(LCM_WIDTH-(gap+rectWidth),(gap+rectHeight)+index*rectHeight+(index+1)*gap,LCM_HEIGHT-gap,((gap+rectHeight)+(index+1)*rectHeight+(index+1)*gap)));
                }else if(i<crossCount+verticalCount*3+crossCount/2-1){
                    int index = i-crossCount-verticalCount*3;
                    arrayList.add(i,new PointInfo(gap+rectWidth+index*rectWidth+(index+1)*gap,gap+(verticalCount/2+1)*(rectHeight+gap),(gap+rectWidth+(index+1)*rectWidth+(index+1)*gap),(gap+(verticalCount/2+1)*(rectHeight+gap)+rectHeight)));
                }else if(i<crossCount+verticalCount*3+crossCount/2-1+crossCount/2-1){
                    int index = i-crossCount-verticalCount*3-crossCount/2+1;
                    arrayList.add(i,new PointInfo((crossCount/2+1)*(gap+rectWidth)+index*rectWidth+(index+1)*gap,gap+(verticalCount/2+1)*(rectHeight+gap),((crossCount/2+1)*(gap+rectWidth)+(index+1)*rectWidth+(index+1)*gap),(gap+(verticalCount/2+1)*(rectHeight+gap)+rectHeight)));
                }else if(i<crossCount+verticalCount*3+crossCount/2-1+crossCount/2-1+crossCount){
                    int index = i-crossCount-verticalCount*3-crossCount/2+1-crossCount/2+1;
                    arrayList.add(i,new PointInfo(index*rectWidth+(index+1)*gap,(verticalCount+1)*(rectHeight+gap)+gap,
                            ((index+1)*rectWidth+(index+1)*gap),((verticalCount+1)*(rectHeight+gap)+rectHeight+gap)));
                }else if(i<crossCount+verticalCount*3+crossCount/2-1+crossCount/2-1+crossCount+(crossCount-3)/2){
                    int index = i-crossCount-verticalCount*3-crossCount/2+1-crossCount/2+1-crossCount;
                    arrayList.add(i,new PointInfo(index*rectWidth+(index+1)*gap+gap+rectWidth,((verticalCount+2)/4)*(rectHeight+gap)+gap,
                            ((index+1)*rectWidth+(index+1)*gap+gap+rectWidth),(((verticalCount+2)/4)*(rectHeight+gap)+rectHeight+gap)));
                }else if(i<crossCount+verticalCount*3+crossCount/2-1+crossCount/2-1+crossCount+(crossCount-3)/2+(crossCount-3)/2){
                    int index = i-crossCount-verticalCount*3-crossCount/2+1-crossCount/2+1-crossCount-(crossCount-3)/2;
                    arrayList.add(i,new PointInfo((crossCount/2+1)*(gap+rectWidth)+index*rectWidth+(index+1)*gap,(verticalCount+2)/4*(rectHeight+gap)+gap,
                            ((crossCount/2+1)*(gap+rectWidth)+(index+1)*rectWidth+(index+1)*gap),(((verticalCount+2)/4)*(rectHeight+gap)+rectHeight+gap)));
                }else if(i<crossCount+verticalCount*3+crossCount/2-1+crossCount/2-1+crossCount+(crossCount-3)/2+(crossCount-3)/2+(crossCount-3)/2){
                    int index = i-crossCount-verticalCount*3-crossCount/2+1-crossCount/2+1-crossCount-(crossCount-3)/2-(crossCount-3)/2;
                    arrayList.add(i,new PointInfo(index*rectWidth+(index+1)*gap+gap+rectWidth,((verticalCount+2)/4*3)*(rectHeight+gap)+gap,((index+1)*rectWidth+(index+1)*gap+gap+rectWidth),(((verticalCount+2)/4*3)*(rectHeight+gap)+rectHeight+gap)));
                }else if(i<crossCount+verticalCount*3+crossCount/2-1+crossCount/2-1+crossCount+(crossCount-3)/2+(crossCount-3)/2+(crossCount-3)/2+(crossCount-3)/2){
                    int index = i-crossCount-verticalCount*3-crossCount/2+1-crossCount/2+1-crossCount-(crossCount-3)/2-(crossCount-3)/2-(crossCount-3)/2;
                    arrayList.add(i,new PointInfo((crossCount/2+1)*(gap+rectWidth)+index*rectWidth+(index+1)*gap,((verticalCount+2)/4*3)*(rectHeight+gap)+gap,
                            ((crossCount/2+1)*(gap+rectWidth)+(index+1)*rectWidth+(index+1)*gap),(((verticalCount+2)/4*3)*(rectHeight+gap)+rectHeight+gap)));
                }

            }

    }

    //触摸事件
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction()==MotionEvent.ACTION_MOVE) {//如果拖动
            canvas.drawLine(mov_x, mov_y, event.getX(), event.getY(), paintRed);//画线
            Log.d("flyz", "onTouchEvent: mov_x "+mov_x+" mov_y "+mov_y+" event.getX() "+event.getX()+" event.getY() "+event.getY());


//
            if(event.getY() > gap && event.getY() < rectHeight+gap){
                for(int i=0; i < crossCount;i++){
                    if(event.getX()>gap+i*(rectWidth+gap) && event.getX()<(i+1)*(rectWidth+gap)){
                        states[i]=true;
                        break;
                    }
                }
            }
            if(event.getX() > gap && event.getX() < rectWidth+gap
                    && event.getY() > gap+rectHeight && event.getY() < LCM_HEIGHT-(gap+rectHeight)){
                for(int i=0; i < verticalCount;i++){
                    if(event.getY()>(gap+rectHeight)+gap+i*(rectHeight+gap) && event.getY()<(gap+rectHeight)+(i+1)*(rectHeight+gap)){
                        states[i+crossCount]=true;
                        break;
                    }
                }
            }
            if(event.getX() > crossCount/2*(gap+rectWidth)+gap
                    && event.getX() < crossCount/2*(gap+rectWidth)+rectWidth+gap
                    && event.getY() > gap+rectHeight && event.getY()< LCM_HEIGHT-(gap+rectHeight)){
                for(int i=0; i < verticalCount;i++){
                    if(event.getY()>(gap+rectHeight)+gap+i*(rectHeight+gap) && event.getY()<(gap+rectHeight)+(i+1)*(rectHeight+gap)){
                        states[i+crossCount+verticalCount]=true;
                        break;
                    }
                }
            }
            if(event.getX() > (crossCount-1)*(gap+rectWidth)+gap
                    && event.getX() < (crossCount-1)*(gap+rectWidth)+rectWidth+gap
                    && event.getY() > gap+rectHeight && event.getY()< LCM_HEIGHT-(gap+rectHeight)){
                for(int i=0; i < verticalCount;i++){
                    if(event.getY()>(gap+rectHeight)+gap+i*(rectHeight+gap) && event.getY()<(gap+rectHeight)+(i+1)*(rectHeight+gap)){
                        states[i+crossCount+verticalCount*2]=true;
                        break;
                    }
                }
            }
            if(event.getY() > (verticalCount/2+1)*(rectHeight+gap)+gap
                    && event.getY() < (verticalCount/2+1)*(rectHeight+gap)+rectHeight+gap
                    && event.getX()>gap+rectWidth && event.getX()< crossCount/2*(gap+rectWidth)){
                for(int i=0; i < crossCount/2-1;i++){
                    if(event.getX()>rectWidth+gap+gap+i*(rectWidth+gap) && event.getX()<rectWidth+gap+(i+1)*(rectWidth+gap)){
                        states[i+crossCount+verticalCount*3]=true;
                        break;
                    }
                }
            }
            if(event.getY() > (verticalCount/2+1)*(rectHeight+gap)+gap
                    && event.getY() < (verticalCount/2+1)*(rectHeight+gap)+rectHeight+gap
                    && event.getX()>(crossCount/2+1)*(gap+rectWidth) && event.getX()< LCM_WIDTH-(gap+rectWidth)){
                for(int i=0; i < crossCount/2-1;i++){
                    if(event.getX()>(crossCount/2+1)*(rectWidth+gap)+gap+i*(rectWidth+gap)
                            && event.getX()<(crossCount/2+1)*(rectWidth+gap)+(i+1)*(rectWidth+gap)){
                        states[i+crossCount+verticalCount*3+crossCount/2-1]=true;
                        break;
                    }
                }
            }
            if(event.getY() > LCM_HEIGHT-(gap+rectHeight) && event.getY() < LCM_HEIGHT){
                for(int i=0; i < crossCount;i++){
                    if(event.getX()>gap+i*(rectWidth+gap) && event.getX()<(i+1)*(rectWidth+gap)){
                        states[i+crossCount+verticalCount*3+crossCount/2-1+crossCount/2-1]=true;
                        break;
                    }
                }
            }
            if(event.getY() > ((verticalCount+2)/4)*(rectHeight+gap)+gap
                    && event.getY() < ((verticalCount+2)/4)*(rectHeight+gap)+rectHeight+gap
                    && event.getX()>gap+rectWidth && event.getX()< crossCount/2*(gap+rectWidth)){
                for(int i=0; i < crossCount/2-1;i++){
                    if(event.getX()>rectWidth+gap+gap+i*(rectWidth+gap) && event.getX()<rectWidth+gap+(i+1)*(rectWidth+gap)){
   //                     states[i+crossCount+verticalCount*3+crossCount/2-1+crossCount/2-1+crossCount]=true;
                        break;
                    }
                }
            }
            if(event.getY() > ((verticalCount+2)/4)*(rectHeight+gap)+gap
                    && event.getY() < ((verticalCount+2)/4)*(rectHeight+gap)+rectHeight+gap
                    && event.getX()>(crossCount/2+1)*(gap+rectWidth) && event.getX()< LCM_WIDTH-(gap+rectWidth)){
                for(int i=0; i < crossCount/2-1;i++){
                    if(event.getX()>(crossCount/2+1)*(rectWidth+gap)+gap+i*(rectWidth+gap)
                            && event.getX()<(crossCount/2+1)*(rectWidth+gap)+(i+1)*(rectWidth+gap)){
  //                      states[i+crossCount+verticalCount*3+crossCount/2-1+crossCount/2-1+crossCount+(crossCount-3)/2]=true;
                        break;
                    }
                }
            }
            if(event.getY() > ((verticalCount+2)/4*3)*(rectHeight+gap)+gap
                    && event.getY() < ((verticalCount+2)/4*3)*(rectHeight+gap)+rectHeight+gap
                    && event.getX()>gap+rectWidth && event.getX()< crossCount/2*(gap+rectWidth)){
                for(int i=0; i < crossCount/2-1;i++){
                    if(event.getX()>rectWidth+gap+gap+i*(rectWidth+gap) && event.getX()<rectWidth+gap+(i+1)*(rectWidth+gap)){
  //                      states[i+crossCount+verticalCount*3+crossCount/2-1+crossCount/2-1+crossCount+(crossCount-3)/2+(crossCount-3)/2]=true;
                        break;
                    }
                }
            }
            if(event.getY() > ((verticalCount+2)/4*3)*(rectHeight+gap)+gap
                    && event.getY() < ((verticalCount+2)/4*3)*(rectHeight+gap)+rectHeight+gap
                    && event.getX()>(crossCount/2+1)*(gap+rectWidth) && event.getX()< LCM_WIDTH-(gap+rectWidth)){
                for(int i=0; i < crossCount/2-1;i++){
                    if(event.getX()>(crossCount/2+1)*(rectWidth+gap)+gap+i*(rectWidth+gap)
                            && event.getX()<(crossCount/2+1)*(rectWidth+gap)+(i+1)*(rectWidth+gap)){
      //                  states[i+crossCount+verticalCount*3+crossCount/2-1+crossCount/2-1+crossCount+(crossCount-3)/2+(crossCount-3)/2+(crossCount-3)/2]=true;
                        break;
                    }
                }
            }
            boolean isAllTure = true;
            for(int i=0; i<totalCount;i++){
                if(!states[i]){
                    isAllTure = false;
                    break;
                }
            }
			/*
            for(int i=0; i<totalCount;i++){
                if(isLineIntersectRectangle(mov_x,mov_y,(int)event.getX(),(int)event.getY(),(int)arrayList.get(i).x1,(int)arrayList.get(i).y1,(int)arrayList.get(i).x2,(int)arrayList.get(i).y2)){
                    Log.d("flyz", "isLineIntersectRectangle: i "+i);
                    if (!states[i]) {
                        states[i]= true;
                    }
                }
            }
			*/

            if(isAllTure){
                //onPause();
                Intent y = new Intent();
                ((Activity)mContext).setResult(1, y);
                ((Activity)mContext).finish();
            }
            invalidate();
        }
        if (event.getAction()==MotionEvent.ACTION_DOWN) {//如果点击
            mov_x=(int) event.getX();
            mov_y=(int) event.getY();

            down_x= event.getX();
            down_y= event.getY();
        }
        mov_x=(int) event.getX();
        mov_y=(int) event.getY();
        return true;
    }




    ArrayList<PointInfo> arrayList =new ArrayList<PointInfo>();

    /**
     * <p> 判断线段是否在矩形内
     * <p>
     * 先看线段所在直线是否与矩形相交，
     * 如果不相交则返回false，
     * 如果相交，
     * 则看线段的两个点是否在矩形的同一边（即两点的x(y)坐标都比矩形的小x(y)坐标小，或者大）,
     * 若在同一边则返回false，
     * 否则就是相交的情况。
     * </p>
     *
     * @param linePointX1 线段起始点x坐标
     * @param linePointY1 线段起始点y坐标
     * @param linePointX2 线段结束点x坐标
     * @param linePointY2 线段结束点y坐标
     * @param rectangleLeftTopX 矩形左上点x坐标
     * @param rectangleLeftTopY 矩形左上点y坐标
     * @param rectangleRightBottomX 矩形右下点x坐标
     * @param rectangleRightBottomY 矩形右下点y坐标
     * @return 是否相交
     */
    private boolean isLineIntersectRectangle(int linePointX1,int linePointY1,int linePointX2,int linePointY2,int rectangleLeftTopX,int rectangleLeftTopY,int rectangleRightBottomX,int rectangleRightBottomY) {

        int lineHeight = linePointY1 - linePointY2;
        int lineWidth = linePointX2 - linePointX1;
        // 计算叉乘
        int c = linePointX1 * linePointY2 - linePointX2 * linePointY1;

        if ((lineHeight * rectangleLeftTopX + lineWidth * rectangleLeftTopY + c >= 0 && lineHeight * rectangleRightBottomX + lineWidth * rectangleRightBottomY + c <= 0)
                || (lineHeight * rectangleLeftTopX + lineWidth * rectangleLeftTopY + c <= 0 && lineHeight * rectangleRightBottomX + lineWidth * rectangleRightBottomY + c >= 0)
                || (lineHeight * rectangleLeftTopX + lineWidth * rectangleRightBottomY + c >= 0 && lineHeight * rectangleRightBottomX + lineWidth * rectangleLeftTopY + c <= 0)
                || (lineHeight * rectangleLeftTopX + lineWidth * rectangleRightBottomY + c <= 0 && lineHeight * rectangleRightBottomX + lineWidth * rectangleLeftTopY + c >= 0)) {
            if (rectangleLeftTopX > rectangleRightBottomX) {
                int temp = rectangleLeftTopX;
                rectangleLeftTopX = rectangleRightBottomX;
                rectangleRightBottomX = temp;
            }
            if (rectangleLeftTopY < rectangleRightBottomY) {
                int temp = rectangleLeftTopY;
                rectangleLeftTopY = rectangleRightBottomY;
                rectangleRightBottomY = temp;
            }
            if ((linePointX1 < rectangleLeftTopX && linePointX2 < rectangleLeftTopX)
                    || (linePointX1 > rectangleRightBottomX && linePointX2 > rectangleRightBottomX)
                    || (linePointY1 > rectangleLeftTopY && linePointY2 > rectangleLeftTopY)
                    || (linePointY1 < rectangleRightBottomY && linePointY2 < rectangleRightBottomY)) {
                return false;
            } else {
                return true;
            }
        } else {
            return false;
        }
    }

}

