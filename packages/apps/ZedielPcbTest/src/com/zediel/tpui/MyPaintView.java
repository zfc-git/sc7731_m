package com.zediel.tpui;

import android.view.View;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

public class MyPaintView extends View {
	private List<Point> allPoints = new ArrayList<Point>();

	public MyPaintView(Context context, AttributeSet attrs) {
		super(context, attrs);
		super.setOnTouchListener(new OnTouchListenerImp());
	}

	private class OnTouchListenerImp implements OnTouchListener {

		public boolean onTouch(View v, MotionEvent event) {
			Point p = new Point((int) event.getX(), (int) event.getY());
			
			if (event.getAction() == MotionEvent.ACTION_DOWN) {
				MyPaintView.this.allPoints = new ArrayList<Point>();
				MyPaintView.this.allPoints.add(p);
			} else if (event.getAction() == MotionEvent.ACTION_UP) {
				//MyPaintView.this.allPoints.clear();
				MyPaintView.this.allPoints.add(p);
				MyPaintView.this.postInvalidate();
			} else if (event.getAction() == MotionEvent.ACTION_MOVE) {
				MyPaintView.this.allPoints.add(p);
				MyPaintView.this.postInvalidate();
			}
			return true;
		}
	}

	@Override
	public void draw(Canvas canvas) {
		Paint p = new Paint();
		p.setColor(Color.YELLOW);
		p.setStrokeWidth(5); 
		if (MyPaintView.this.allPoints.size() > 1) {
		
			Iterator<Point> iter = MyPaintView.this.allPoints.iterator();
			Point first = null;
			Point last = null;
			while (iter.hasNext()) {
				if (first == null) {
					first = (Point) iter.next();
				} else {
					if (last != null) {
						first = last;
					}
					last = (Point) iter.next();
					canvas.drawLine(first.x, first.y, last.x, last.y, p);
				}
			}
		}
	}
}
