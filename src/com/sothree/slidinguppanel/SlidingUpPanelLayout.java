package com.sothree.slidinguppanel;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.v4.view.MotionEventCompat;
import android.support.v4.view.ViewCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;

import com.nineoldandroids.view.animation.AnimatorProxy;
import com.sothree.slidinguppanel.demo.R;

public class SlidingUpPanelLayout extends ViewGroup {

    private static final String TAG = SlidingUpPanelLayout.class.getSimpleName();

    /**
     * 默认panel高度
     */
    private static final int DEFAULT_PANEL_HEIGHT = 68; // dp;

    /**
     * 默认阴影的高度
     */
    private static final int DEFAULT_SHADOW_HEIGHT = 4; // dp;

    /**
     * 默认蒙层颜色
     */
    private static final int DEFAULT_FADE_COLOR = 0x99000000;

    /**
     * 默认最低快速滑动的阀值
     */
    private static final int DEFAULT_MIN_FLING_VELOCITY = 400; // dips per second
    
    /**
     * 默认是否在mMainview上加一层蒙层
     */
    private static final boolean DEFAULT_OVERLAY_FLAG = false;
    
    /**
     * 默认定义要解析的属性
     */
    private static final int[] DEFAULT_ATTRS = new int[] {
        android.R.attr.gravity
    };

    /**
     * fling最低速度阀值
     */
    private int mMinFlingVelocity = DEFAULT_MIN_FLING_VELOCITY;

    /**
     * 蒙层颜色
     */
    private int mCoveredFadeColor = DEFAULT_FADE_COLOR;

    /**
     * 默认定义在滑动时，mMainView的偏移值
     */
    private static final int DEFAULT_PARALAX_OFFSET = 0;

    /**
     * 画蒙层的paint
     */
    private final Paint mCoveredFadePaint = new Paint();

    /**
     * 画阴影的drawable
     */
    private final Drawable mShadowDrawable;

    /**
     * slideable view折叠时的高度 单位像素
     */
    private int mPanelHeight = -1;

    /**
     * 阴影的高度
     */
    private int mShadowHeight = -1;

    /**
     * 定义mMainView的最大偏移值
     */
    private int mParalaxOffset = -1;

    /**
     * 若为true，定义slideable view向上滑动为展开
     */
    private boolean mIsSlidingUp;

    /**
     * 若为true，表示panel可以滑动
     */
    private boolean mCanSlide;

    /**
     * 若为false 表示会在mMainview上加上一层蒙层
     */
    private boolean mOverlayContent = DEFAULT_OVERLAY_FLAG;

    /**
     * 可用来拖动的view
     */
    private View mDragView;

    /**
     * 对应mDragView
     */
    private int mDragViewResId = -1;

    /**
     * 可被滑动的view
     */
    private View mSlideableView;

    /**
     * main view 一般是第一个索引的child view
     */
    private View mMainView;

    /**
     * 定义可滑动slideable view的状态
     */
    private enum SlideState {
        EXPANDED,
        COLLAPSED,
        ANCHORED,//类似锚点的功能
    }
    //记录当前slideable view的状态
    private SlideState mSlideState = SlideState.COLLAPSED;

    /**
     * 当前slideable view的滑动位置 是个比值 range[0,1] 0 = 展开， 1 = 收起
     */
    private float mSlideOffset;

    /**
     * slideable view能滑动的最大距离 单位像素
     */
    private int mSlideRange;

    /**
     * 若为true 表示不能够继续拖动
     */
    private boolean mIsUnableToDrag;

    /**
     * 一个flag 来标示是否激活滑动功能
     */
    private boolean mIsSlidingEnabled;

    /**
     * 若为true，此flag表示drag view想自己处理内部触摸事件，drag view可以水平滚动和处理点击事件
     * 默认这个值是false
     */
    private boolean mIsUsingDragViewTouchEvents;

    /**
     * 定义最低可滑动的距离 单位像素
     */
    private final int mScrollTouchSlop;

    //触摸事件down时，会记录point的x、y值
    private float mInitialMotionX;
    private float mInitialMotionY;
    
    /**
     * 锚点 有效值范围[0,1]
     */
    private float mAnchorPoint = 0.f;

    /**
     * Panel滑动动作监听
     */
    private PanelSlideListener mPanelSlideListener;

    /**
     * 辅助类 用于处理滑动的细节
     */
    private final ViewDragHelper mDragHelper;

    /**
     * 标示是否需要重新初始化
     */
    private boolean mFirstLayout = true;

    /**
     * 画main view和蒙层的区域大小
     */
    private final Rect mTmpRect = new Rect();

    /**
     * Panel滑动动作监听
     */
    public interface PanelSlideListener {
        
    	/**
    	 * 正在drag时，若有有效的滑动距离，会回调此函数
    	 * @param panel
    	 * @param slideOffset
    	 */
        public void onPanelSlide(View panel, float slideOffset);
        
        /**
         * Panel收起时回调
         * @param panel
         */
        public void onPanelCollapsed(View panel);

        /**
         * Panel展开时回调
         * @param panel
         */
        public void onPanelExpanded(View panel);

        /**
         * Panel滑到锚点时，会回调
         * @param panel
         */
        public void onPanelAnchored(View panel);
    }

    /**
     * 如果你不想实现PanelSlideListener的全部函数，可使用此
     */
    public static class SimplePanelSlideListener implements PanelSlideListener {
        @Override
        public void onPanelSlide(View panel, float slideOffset) {
        }
        @Override
        public void onPanelCollapsed(View panel) {
        }
        @Override
        public void onPanelExpanded(View panel) {
        }
        @Override
        public void onPanelAnchored(View panel) {
        }
    }

    //构造函数
    public SlidingUpPanelLayout(Context context) {
        this(context, null);
    }

    //构造函数
    public SlidingUpPanelLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    //构造函数
    public SlidingUpPanelLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        
        //兼容一些android提供的可视化工具做的处理
        if(isInEditMode()) {
            mShadowDrawable = null;
            mScrollTouchSlop = 0;
            mDragHelper = null;
            return;
        }
        
        //解析系统属性
        if (attrs != null) {
            TypedArray defAttrs = context.obtainStyledAttributes(attrs, DEFAULT_ATTRS);

            if (defAttrs != null) {
                int gravity = defAttrs.getInt(0, Gravity.NO_GRAVITY);
                if (gravity != Gravity.TOP && gravity != Gravity.BOTTOM) {
                    throw new IllegalArgumentException("gravity must be set to either top or bottom");
                }
                mIsSlidingUp = gravity == Gravity.BOTTOM;
            }

            defAttrs.recycle();

            TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.SlidingUpPanelLayout);

            //解析自定义的属性
            if (ta != null) {
                mPanelHeight = ta.getDimensionPixelSize(R.styleable.SlidingUpPanelLayout_panelHeight, -1);
                mShadowHeight = ta.getDimensionPixelSize(R.styleable.SlidingUpPanelLayout_shadowHeight, -1);
                mParalaxOffset = ta.getDimensionPixelSize(R.styleable.SlidingUpPanelLayout_paralaxOffset, -1);

                mMinFlingVelocity = ta.getInt(R.styleable.SlidingUpPanelLayout_flingVelocity, DEFAULT_MIN_FLING_VELOCITY);
                mCoveredFadeColor = ta.getColor(R.styleable.SlidingUpPanelLayout_fadeColor, DEFAULT_FADE_COLOR);

                mDragViewResId = ta.getResourceId(R.styleable.SlidingUpPanelLayout_dragView, -1);

                mOverlayContent = ta.getBoolean(R.styleable.SlidingUpPanelLayout_overlay,DEFAULT_OVERLAY_FLAG);
            }

            ta.recycle();
        }

        //若在xml为定义某些属性，会在此初始化值
        final float density = context.getResources().getDisplayMetrics().density;
        if (mPanelHeight == -1) {
            mPanelHeight = (int) (DEFAULT_PANEL_HEIGHT * density + 0.5f);
        }
        if (mShadowHeight == -1) {
            mShadowHeight = (int) (DEFAULT_SHADOW_HEIGHT * density + 0.5f);
        }
        if (mParalaxOffset == -1) {
            mParalaxOffset = (int) (DEFAULT_PARALAX_OFFSET * density);
        }
        // If the shadow height is zero, don't show the shadow
        if (mShadowHeight > 0) {
            if (mIsSlidingUp) {
                mShadowDrawable = getResources().getDrawable(R.drawable.above_shadow);
            } else {
                mShadowDrawable = getResources().getDrawable(R.drawable.below_shadow);
            }

        } else {
            mShadowDrawable = null;
        }

        setWillNotDraw(false);

        //用来处理滑动的工具类
        mDragHelper = ViewDragHelper.create(this, 0.5f, new DragHelperCallback());
        mDragHelper.setMinVelocity(mMinFlingVelocity * density);

        mCanSlide = true;
        mIsSlidingEnabled = true;

        ViewConfiguration vc = ViewConfiguration.get(context);
        mScrollTouchSlop = vc.getScaledTouchSlop();
    }

    /**
     * 在view inflate后，初始化mDragView
     */
    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        if (mDragViewResId != -1) {
            mDragView = findViewById(mDragViewResId);
        }
    }

    /**
     * 设置蒙层的颜色
     * @param color
     */
    public void setCoveredFadeColor(int color) {
        mCoveredFadeColor = color;
        invalidate();
    }

    /**
     * 获取蒙层的颜色
     * @return
     */
    public int getCoveredFadeColor() {
        return mCoveredFadeColor;
    }

    /**
     * 设置是否激活滑动功能
     * @param enabled
     */
    public void setSlidingEnabled(boolean enabled) {
        mIsSlidingEnabled = enabled;
    }

    /**
     * 设置slideable view折叠时的高度
     * @param val 单位像素
     */
    public void setPanelHeight(int val) {
        mPanelHeight = val;
        requestLayout();
    }

    /**
     * 获取slideable view折叠时的高度
     */
    public int getPanelHeight() {
        return mPanelHeight;
    }

    /**
     * 获取mMainView的偏移值
     */
    public int getCurrentParalaxOffset() {
        int offset = (int)(mParalaxOffset * (1 - mSlideOffset));
        return mIsSlidingUp ? -offset : offset;
    }

    /**
     * 设置回调监听函数
     * @param listener
     */
    public void setPanelSlideListener(PanelSlideListener listener) {
        mPanelSlideListener = listener;
    }

    /**
     * 设置可用来拖动的view，若为NULL，表示允许整个drag view响应拖动
     * @param dragView
     */
    public void setDragView(View dragView) {
        mDragView = dragView;
    }

    /**
     * 设置锚点
     * @param anchorPoint 有效值范围[0,1]
     */
    public void setAnchorPoint(float anchorPoint) {
        if (anchorPoint > 0 && anchorPoint < 1)
            mAnchorPoint = anchorPoint;
    }

    /**
     * 若为false 表示会在mMainview上加上一层蒙层
     * @param overlayed
     */
    public void setOverlayed(boolean overlayed) {
        mOverlayContent = overlayed;
    }

    /**
     * 获取是否在mMainview上加上一层蒙层
     * @return
     */
    public boolean isOverlayed() {
        return mOverlayContent;
    }

    /**
     * Panel有滑动时，用于做分发
     * @param panel
     */
    void dispatchOnPanelSlide(View panel) {
        if (mPanelSlideListener != null) {
            mPanelSlideListener.onPanelSlide(panel, mSlideOffset);
        }
    }
    
    /**
     * Panel展开时，用于做分发
     * @param panel
     */
    void dispatchOnPanelExpanded(View panel) {
        if (mPanelSlideListener != null) {
            mPanelSlideListener.onPanelExpanded(panel);
        }
        sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
    }

    /**
     * Panel收起时，用于做分发
     * @param panel
     */
    void dispatchOnPanelCollapsed(View panel) {
        if (mPanelSlideListener != null) {
            mPanelSlideListener.onPanelCollapsed(panel);
        }
        sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
    }

    /**
     * Panel滑到锚点时，用于做分发
     * @param panel
     */
    void dispatchOnPanelAnchored(View panel) {
        if (mPanelSlideListener != null) {
            mPanelSlideListener.onPanelAnchored(panel);
        }
        sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
    }

    /**
     * 根据当前的view的位置判断是显示还是隐藏
     */
    void updateObscuredViewVisibility() {
        if (getChildCount() == 0) {
            return;
        }
        final int leftBound = getPaddingLeft();
        final int rightBound = getWidth() - getPaddingRight();
        final int topBound = getPaddingTop();
        final int bottomBound = getHeight() - getPaddingBottom();
        final int left;
        final int right;
        final int top;
        final int bottom;
        if (mSlideableView != null && hasOpaqueBackground(mSlideableView)) {
            left = mSlideableView.getLeft();
            right = mSlideableView.getRight();
            top = mSlideableView.getTop();
            bottom = mSlideableView.getBottom();
        } else {
            left = right = top = bottom = 0;
        }
        View child = getChildAt(0);
        final int clampedChildLeft = Math.max(leftBound, child.getLeft());
        final int clampedChildTop = Math.max(topBound, child.getTop());
        final int clampedChildRight = Math.min(rightBound, child.getRight());
        final int clampedChildBottom = Math.min(bottomBound, child.getBottom());
        final int vis;
        //计算若mMainView完全被覆盖时，就隐藏
        if (clampedChildLeft >= left && clampedChildTop >= top &&
                clampedChildRight <= right && clampedChildBottom <= bottom) {
            vis = INVISIBLE;
        } else {
            vis = VISIBLE;
        }
        child.setVisibility(vis);
    }

    /**
     * 设置所有childview可见状态为VISIBLE
     */
    void setAllChildrenVisible() {
        for (int i = 0, childCount = getChildCount(); i < childCount; i++) {
            final View child = getChildAt(i);
            if (child.getVisibility() == INVISIBLE) {
                child.setVisibility(VISIBLE);
            }
        }
    }

    /**
     * 给定view背景是否透明
     * @param v
     * @return
     */
    private static boolean hasOpaqueBackground(View v) {
        final Drawable bg = v.getBackground();
        return bg != null && bg.getOpacity() == PixelFormat.OPAQUE;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mFirstLayout = true;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mFirstLayout = true;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        final int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        final int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        final int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        //目前只支持 MATCH_PARENT
        if (widthMode != MeasureSpec.EXACTLY) {
            throw new IllegalStateException("Width must have an exact value or MATCH_PARENT");
        } else if (heightMode != MeasureSpec.EXACTLY) {
            throw new IllegalStateException("Height must have an exact value or MATCH_PARENT");
        }

        int layoutHeight = heightSize - getPaddingTop() - getPaddingBottom();
        int panelHeight = mPanelHeight;

        final int childCount = getChildCount();

        if (childCount > 2) {
            Log.e(TAG, "onMeasure: More than two child views are not supported.");
        } else if (getChildAt(1).getVisibility() == GONE) {
            panelHeight = 0;
        }

        mSlideableView = null;
        mCanSlide = false;

        //measure
        for (int i = 0; i < childCount; i++) {
            final View child = getChildAt(i);
            final LayoutParams lp = (LayoutParams) child.getLayoutParams();

            int height = layoutHeight;
            if (child.getVisibility() == GONE) {
                lp.dimWhenOffset = false;
                continue;
            }

            if (i == 1) {
                lp.slideable = true;//设置第二个child view为可滑动的view
                lp.dimWhenOffset = true;
                mSlideableView = child;
                mCanSlide = true;//标示panel为可滑动状态
            } else {
                if (!mOverlayContent) {
                    height -= panelHeight;
                }
                mMainView = child;
            }

            //子child测量
            int childWidthSpec;
            if (lp.width == LayoutParams.WRAP_CONTENT) {
                childWidthSpec = MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.AT_MOST);
            } else if (lp.width == LayoutParams.MATCH_PARENT) {
                childWidthSpec = MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY);
            } else {
                childWidthSpec = MeasureSpec.makeMeasureSpec(lp.width, MeasureSpec.EXACTLY);
            }

            int childHeightSpec;
            if (lp.height == LayoutParams.WRAP_CONTENT) {
                childHeightSpec = MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST);
            } else if (lp.height == LayoutParams.MATCH_PARENT) {
                childHeightSpec = MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY);
            } else {
                childHeightSpec = MeasureSpec.makeMeasureSpec(lp.height, MeasureSpec.EXACTLY);
            }
            //子view measure调用
            child.measure(childWidthSpec, childHeightSpec);
        }

        setMeasuredDimension(widthSize, heightSize);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        final int paddingLeft = getPaddingLeft();
        final int paddingTop = getPaddingTop();
        final int slidingTop = getSlidingTop();

        final int childCount = getChildCount();

        //根据当前mSlideState，初始化mSlideOffset值
        if (mFirstLayout) {
            switch (mSlideState) {
            case EXPANDED:
                mSlideOffset = mCanSlide ? 0.f : 1.f;
                break;
            case ANCHORED:
                mSlideOffset = mCanSlide ? mAnchorPoint : 1.f;
                break;
            default:
                mSlideOffset = 1.f;
                break;
            }
        }

        for (int i = 0; i < childCount; i++) {
            final View child = getChildAt(i);

            if (child.getVisibility() == GONE) {
                continue;
            }

            final LayoutParams lp = (LayoutParams) child.getLayoutParams();
            final int childHeight = child.getMeasuredHeight();

            //若当前的view是slideable view，则计算其滑动的最大距离值
            if (lp.slideable) {
                mSlideRange = childHeight - mPanelHeight;
            }

            int childTop;
            //计算top的值 ，这里mSlideOffset是可变因子
            if (mIsSlidingUp) {
                childTop = lp.slideable ? slidingTop + (int) (mSlideRange * mSlideOffset) : paddingTop;
            } else {
                childTop = lp.slideable ? slidingTop - (int) (mSlideRange * mSlideOffset) : paddingTop;
                if (!lp.slideable && !mOverlayContent) {
                    childTop += mPanelHeight;
                }
            }
            final int childBottom = childTop + childHeight;
            final int childLeft = paddingLeft;
            final int childRight = childLeft + child.getMeasuredWidth();
            
            //完成child view的layout
            child.layout(childLeft, childTop, childRight, childBottom);
        }

        if (mFirstLayout) {
            updateObscuredViewVisibility();
        }

        mFirstLayout = false;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        //view size发送变化时处理
        if (h != oldh) {
            mFirstLayout = true;
        }
    }

    /**
     * 若为true，此flag表示drag view想自己处理内部触摸事件，此时drag view可以处理水平滚动和点击事件
     * 默认这个值是false
     */
    public void setEnableDragViewTouchEvents(boolean enabled) {
        mIsUsingDragViewTouchEvents = enabled;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        final int action = MotionEventCompat.getActionMasked(ev);

        if (!mCanSlide || !mIsSlidingEnabled || (mIsUnableToDrag && action != MotionEvent.ACTION_DOWN)) {
            //滑动状态清空
        	mDragHelper.cancel();
            return super.onInterceptTouchEvent(ev);
        }

        if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP) {
        	//滑动状态清空
        	mDragHelper.cancel();
            return false;
        }

        final float x = ev.getX();
        final float y = ev.getY();
        //若为true，表示drag view将不能处理内部的触摸事件
        boolean interceptTap = false;

        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                mIsUnableToDrag = false;
                mInitialMotionX = x;
                mInitialMotionY = y;
                //满足此条件 表示拦截此事件  接下来会正式交给drag view的触摸事件
                if (isDragViewUnder((int) x, (int) y) && !mIsUsingDragViewTouchEvents) {
                    interceptTap = true;
                }
                break;
            }

            case MotionEvent.ACTION_MOVE: {
                final float adx = Math.abs(x - mInitialMotionX);
                final float ady = Math.abs(y - mInitialMotionY);
                final int dragSlop = mDragHelper.getTouchSlop();

                //处理可能有的横向滚动事件
                if (mIsUsingDragViewTouchEvents) {
                	//满足此条件，处理横向触摸事件
                    if (adx > mScrollTouchSlop && ady < mScrollTouchSlop) {
                        return super.onInterceptTouchEvent(ev);
                    }
                    //满足此条件，表示有有效的竖向触摸事件，那么若触摸事件落在drag view上，需优先处理竖向触摸事件，忽略横向触摸事件
                    else if (ady > mScrollTouchSlop) {
                        interceptTap = isDragViewUnder((int) x, (int) y);
                    }
                }

                if ((ady > dragSlop && adx > ady) || !isDragViewUnder((int) x, (int) y)) {
                	//滑动状态清空
                	mDragHelper.cancel();
                    mIsUnableToDrag = true;
                    return false;
                }
                break;
            }
        }

        final boolean interceptForDrag = mDragHelper.shouldInterceptTouchEvent(ev);

        return interceptForDrag || interceptTap;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (!mCanSlide || !mIsSlidingEnabled) {
            return super.onTouchEvent(ev);
        }

        //具体的滑动计算处理
        mDragHelper.processTouchEvent(ev);

        final int action = ev.getAction();
        boolean wantTouchEvents = true;

        switch (action & MotionEventCompat.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN: {
                final float x = ev.getX();
                final float y = ev.getY();
                mInitialMotionX = x;
                mInitialMotionY = y;
                break;
            }

            case MotionEvent.ACTION_UP: {
                final float x = ev.getX();
                final float y = ev.getY();
                final float dx = x - mInitialMotionX;
                final float dy = y - mInitialMotionY;
                final int slop = mDragHelper.getTouchSlop();
                View dragView = mDragView != null ? mDragView : mSlideableView;
                if (dx * dx + dy * dy < slop * slop &&
                        isDragViewUnder((int) x, (int) y)) {
                    dragView.playSoundEffect(SoundEffectConstants.CLICK);
                    //点击事件处理 展开或收起
                    if (!isExpanded() && !isAnchored()) {
                        expandPane(mAnchorPoint);
                    } else {
                        collapsePane();
                    }
                    break;
                }
                break;
            }
        }

        return wantTouchEvents;
    }

    /**
     * 判断当前point是否落在dragView这个view上
     * @param x
     * @param y
     * @return
     */
    private boolean isDragViewUnder(int x, int y) {
        View dragView = mDragView != null ? mDragView : mSlideableView;
        if (dragView == null) return false;
        int[] viewLocation = new int[2];
        dragView.getLocationOnScreen(viewLocation);
        int[] parentLocation = new int[2];
        this.getLocationOnScreen(parentLocation);
        int screenX = parentLocation[0] + x;
        int screenY = parentLocation[1] + y;
        return screenX >= viewLocation[0] && screenX < viewLocation[0] + dragView.getWidth() &&
                screenY >= viewLocation[1] && screenY < viewLocation[1] + dragView.getHeight();
    }

    /**
     * 若当前支持滑动，展开slideable view
     * @param pane
     * @param initialVelocity
     * @param mSlideOffset
     * @return
     */
    private boolean expandPane(View pane, int initialVelocity, float mSlideOffset) {
        return mFirstLayout || smoothSlideTo(mSlideOffset, initialVelocity);
    }

    /**
     * 若当前支持滑动，收起slideable view
     * @param pane
     * @param initialVelocity
     * @return
     */
    private boolean collapsePane(View pane, int initialVelocity) {
        return mFirstLayout || smoothSlideTo(1.f, initialVelocity);
    }

    /**
     * 若mIsSlidingUp为true，计算slideable view完全展开的top值
     * 若mIsSlidingUp为false，计算slideable view完全收起的top值
     * @return
     */
    private int getSlidingTop() {
        if (mSlideableView != null) {
            return mIsSlidingUp
                    ? getMeasuredHeight() - getPaddingBottom() - mSlideableView.getMeasuredHeight()
                    : getPaddingTop();
        }

        return getMeasuredHeight() - getPaddingBottom();
    }

    /**
     * 若当前支持滑动，收起slideable view
     * @param pane
     * @param initialVelocity
     * @return
     */
    public boolean collapsePane() {
        return collapsePane(mSlideableView, 0);
    }

    /**
     * 若当前支持滑动，展开slideable view
     * @param pane
     * @param initialVelocity
     * @param mSlideOffset
     * @return
     */
    public boolean expandPane() {
        return expandPane(0);
    }

    /**
     * 展开slideable view
     * @param mSlideOffset 定义展开slideable view到上面位置 值范围是0-1
     * @return
     */
    public boolean expandPane(float mSlideOffset) {
        if (!isPaneVisible()) {
            showPane();
        }
        return expandPane(mSlideableView, 0, mSlideOffset);
    }

    /**
     * 判断当前slideable view是否为展开状态
     * @return 若为true 表示状态为展开
     */
    public boolean isExpanded() {
        return mSlideState == SlideState.EXPANDED;
    }

    /**
     * 判断当前slideable view是否为锚点状态
     * @return 若为true 表示slideable view在锚点位置停留
     */
    public boolean isAnchored() {
        return mSlideState == SlideState.ANCHORED;
    }

    /**
     * 获取当前是否可以滑动
     * @return
     */
    public boolean isSlideable() {
        return mCanSlide;
    }

    /**
     * slideable view的是否可见
     * @return
     */
    public boolean isPaneVisible() {
        if (getChildCount() < 2) {
            return false;
        }
        View slidingPane = getChildAt(1);
        return slidingPane.getVisibility() == View.VISIBLE;
    }

    /**
     * 设置slideable view为可见
     */
    public void showPane() {
        if (getChildCount() < 2) {
            return;
        }
        View slidingPane = getChildAt(1);
        slidingPane.setVisibility(View.VISIBLE);
        requestLayout();
    }

    /**
     * 设置slideable view为不可见(gone)
     */
    public void hidePane() {
        if (mSlideableView == null) {
            return;
        }
        mSlideableView.setVisibility(View.GONE);
        requestLayout();
    }

    /**
     * 触摸手势在drag下，处理mMainView的滑动
     * @param newTop
     */
    private void onPanelDragged(int newTop) {
        final int topBound = getSlidingTop();
        mSlideOffset = mIsSlidingUp
                ? (float) (newTop - topBound) / mSlideRange
                : (float) (topBound - newTop) / mSlideRange;
        dispatchOnPanelSlide(mSlideableView);

        if (mParalaxOffset > 0) {
        	//开始计算mMainView的位移
            int mainViewOffset = getCurrentParalaxOffset();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                mMainView.setTranslationY(mainViewOffset);
            } else {
                AnimatorProxy.wrap(mMainView).setTranslationY(mainViewOffset);
            }
        }
    }

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        final LayoutParams lp = (LayoutParams) child.getLayoutParams();
        boolean result;
        //必须需要save后，来clipRect
        final int save = canvas.save(Canvas.CLIP_SAVE_FLAG);

        boolean drawScrim = false;

        if (mCanSlide && !lp.slideable && mSlideableView != null) {
            // Clip against the slider; no sense drawing what will immediately be covered,
            // Unless the panel is set to overlay content
            if (!mOverlayContent) {
                canvas.getClipBounds(mTmpRect);
                if (mIsSlidingUp) {
                    mTmpRect.bottom = Math.min(mTmpRect.bottom, mSlideableView.getTop());
                } else {
                    mTmpRect.top = Math.max(mTmpRect.top, mSlideableView.getBottom());
                }
                canvas.clipRect(mTmpRect);
            }
            // <1表示非完全收起,需要画蒙层
            if (mSlideOffset < 1) {
                drawScrim = true;
            }
        }

        result = super.drawChild(canvas, child, drawingTime);
        canvas.restoreToCount(save);

        //非完全收起情况下，需要画一个半透明的蒙层
        if (drawScrim) {
            final int baseAlpha = (mCoveredFadeColor & 0xff000000) >>> 24;//取alpha值
            final int imag = (int) (baseAlpha * (1 - mSlideOffset));//根据滑动的距离越大，蒙层透明度越低
            final int color = imag << 24 | (mCoveredFadeColor & 0xffffff);
            mCoveredFadePaint.setColor(color);
            canvas.drawRect(mTmpRect, mCoveredFadePaint);
        }

        return result;
    }

    /**
     * <code>mSlideableView</code>滑动到指定位置，有动画效果
     * @param slideOffset
     * @param velocity
     * @return
     */
    boolean smoothSlideTo(float slideOffset, int velocity) {
    	//条件判断是否能滑动
    	if (!mCanSlide) {
            // Nothing to do.
            return false;
        }

        final int topBound = getSlidingTop();
        //计算滑动到最终坐标的y值
        int y = mIsSlidingUp
                ? (int) (topBound + slideOffset * mSlideRange)
                : (int) (topBound - slideOffset * mSlideRange);

        //开始准备滑动mSlideableView到指定位置
        if (mDragHelper.smoothSlideViewTo(mSlideableView, mSlideableView.getLeft(), y)) {
            setAllChildrenVisible();
            //刷新view
            ViewCompat.postInvalidateOnAnimation(this);
            return true;
        }
        return false;
    }

    @Override
    public void computeScroll() {
    	//在滑动中，若此时是非move事件触发的，DragHelper会把当前的mDragState设置为STATE_SETTLING。此时会进入此分支，来处理接下来的位移动画
        if (mDragHelper.continueSettling(true)) {
            if (!mCanSlide) {
                mDragHelper.abort();
                return;
            }

            ViewCompat.postInvalidateOnAnimation(this);
        }
    }

    @Override
    public void draw(Canvas c) {
        super.draw(c);

        if (mSlideableView == null) {
            // No need to draw a shadow if we don't have one.
            return;
        }
        
        //计算阴影的范围
        final int right = mSlideableView.getRight();
        final int top;
        final int bottom;
        if (mIsSlidingUp) {
            top = mSlideableView.getTop() - mShadowHeight;
            bottom = mSlideableView.getTop();
        } else {
            top = mSlideableView.getBottom();
            bottom = mSlideableView.getBottom() + mShadowHeight;
        }
        final int left = mSlideableView.getLeft();
        
        //画阴影
        if (mShadowDrawable != null) {
            mShadowDrawable.setBounds(left, top, right, bottom);
            mShadowDrawable.draw(c);
        }
    }

    protected boolean canScroll(View v, boolean checkV, int dx, int x, int y) {
        if (v instanceof ViewGroup) {
            final ViewGroup group = (ViewGroup) v;
            final int scrollX = v.getScrollX();
            final int scrollY = v.getScrollY();
            final int count = group.getChildCount();
            // Count backwards - let topmost views consume scroll distance first.
            for (int i = count - 1; i >= 0; i--) {
                final View child = group.getChildAt(i);
                if (x + scrollX >= child.getLeft() && x + scrollX < child.getRight() &&
                        y + scrollY >= child.getTop() && y + scrollY < child.getBottom() &&
                        canScroll(child, true, dx, x + scrollX - child.getLeft(),
                                y + scrollY - child.getTop())) {
                    return true;
                }
            }
        }
        return checkV && ViewCompat.canScrollHorizontally(v, -dx);
    }


    @Override
    protected ViewGroup.LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams();
    }

    @Override
    protected ViewGroup.LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) {
        return p instanceof MarginLayoutParams
                ? new LayoutParams((MarginLayoutParams) p)
                : new LayoutParams(p);
    }

    @Override
    protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
        return p instanceof LayoutParams && super.checkLayoutParams(p);
    }

    @Override
    public ViewGroup.LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new LayoutParams(getContext(), attrs);
    }

    @Override
    protected Parcelable onSaveInstanceState() {
    	//保存值
        Parcelable superState = super.onSaveInstanceState();

        SavedState ss = new SavedState(superState);
        ss.mSlideState = mSlideState;

        return ss;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
    	//恢复值
        SavedState ss = (SavedState) state;
        super.onRestoreInstanceState(ss.getSuperState());
        mSlideState = ss.mSlideState;
    }

    private class DragHelperCallback extends ViewDragHelper.Callback {

    	//这是一个开关，若返回false，表示不可以滑动。
    	//若为true表示可以滑动，并且ViewDragHelper会把state设置为STATE_DRAGGING
        @Override
        public boolean tryCaptureView(View child, int pointerId) {
            if (mIsUnableToDrag) {
                return false;
            }

            return ((LayoutParams) child.getLayoutParams()).slideable;
        }

        //ViewDragHelper维护的状态发生变化时，会回调此函数
        @Override
        public void onViewDragStateChanged(int state) {
            int anchoredTop = (int)(mAnchorPoint*mSlideRange);

            //在STATE_IDLE状态下判断，切换mSlideState的值。在ViewDragHelper的其他状态判断没有意义
            if (mDragHelper.getViewDragState() == ViewDragHelper.STATE_IDLE) {
                if (mSlideOffset == 0) {
                    if (mSlideState != SlideState.EXPANDED) {
                        updateObscuredViewVisibility();
                        dispatchOnPanelExpanded(mSlideableView);
                        mSlideState = SlideState.EXPANDED;
                    }
                } else if (mSlideOffset == (float)anchoredTop/(float)mSlideRange) {
                    if (mSlideState != SlideState.ANCHORED) {
                        updateObscuredViewVisibility();
                        dispatchOnPanelAnchored(mSlideableView);
                        mSlideState = SlideState.ANCHORED;
                    }
                } else if (mSlideState != SlideState.COLLAPSED) {
                    dispatchOnPanelCollapsed(mSlideableView);
                    mSlideState = SlideState.COLLAPSED;
                }
            }
        }

        //在tryCaptureView返回true后，会回调此函数
        @Override
        public void onViewCaptured(View capturedChild, int activePointerId) {
            // Make all child views visible in preparation for sliding things around
            setAllChildrenVisible();
        }

        //当panel位置有偏移时，会回调此函数
        @Override
        public void onViewPositionChanged(View changedView, int left, int top, int dx, int dy) {
            onPanelDragged(top);
            invalidate();
        }

        //当cancel或up事件触发时，会回调此函数，后二个参数记录触发时的事件轨迹速度
        @Override
        public void onViewReleased(View releasedChild, float xvel, float yvel) {
            //保存滑动的最终位置y值
        	int top = mIsSlidingUp
                    ? getSlidingTop()
                    : getSlidingTop() - mSlideRange;

            //若设置了锚点，那么计算滑动最终位置时，考虑锚点的位置
            if (mAnchorPoint != 0) {
                int anchoredTop;
                float anchorOffset;
                if (mIsSlidingUp) {
                    anchoredTop = (int)(mAnchorPoint*mSlideRange);
                    anchorOffset = (float)anchoredTop/(float)mSlideRange;
                } else {
                    anchoredTop = mPanelHeight - (int)(mAnchorPoint*mSlideRange);
                    anchorOffset = (float)(mPanelHeight - anchoredTop)/(float)mSlideRange;
                }

                if (yvel > 0 || (yvel == 0 && mSlideOffset >= (1f+anchorOffset)/2)) {
                    top += mSlideRange;
                } else if (yvel == 0 && mSlideOffset < (1f+anchorOffset)/2
                                    && mSlideOffset >= anchorOffset/2) {
                    top += mSlideRange * mAnchorPoint;
                }

            } else if (yvel > 0 || (yvel == 0 && mSlideOffset > 0.5f)) {
                top += mSlideRange;
            }

            //计算好滑动的最终位置后，开始滑动view
            mDragHelper.settleCapturedViewAt(releasedChild.getLeft(), top);
            invalidate();
        }

        //这个很重要，要实现竖向滑动，这个必须要重写
        @Override
        public int getViewVerticalDragRange(View child) {
            return mSlideRange;
        }

        //对top值进行修正，限制值范围在(topBound,bottomBound)之间
        @Override
        public int clampViewPositionVertical(View child, int top, int dy) {
            final int topBound;
            final int bottomBound;
            if (mIsSlidingUp) {
                topBound = getSlidingTop();
                bottomBound = topBound + mSlideRange;
            } else {
                bottomBound = getPaddingTop();
                topBound = bottomBound - mSlideRange;
            }

            return Math.min(Math.max(top, topBound), bottomBound);
        }
    }

    public static class LayoutParams extends ViewGroup.MarginLayoutParams {
        private static final int[] ATTRS = new int[] {
            android.R.attr.layout_weight
        };

        /**
         * 若为true，表示panel可滑动
         */
        boolean slideable;

        /**
         * True if this view should be drawn dimmed
         * when it's been offset from its default position.
         */
        boolean dimWhenOffset;

        Paint dimPaint;

        public LayoutParams() {
            super(MATCH_PARENT, MATCH_PARENT);
        }

        public LayoutParams(int width, int height) {
            super(width, height);
        }

        public LayoutParams(android.view.ViewGroup.LayoutParams source) {
            super(source);
        }

        public LayoutParams(MarginLayoutParams source) {
            super(source);
        }

        public LayoutParams(LayoutParams source) {
            super(source);
        }

        public LayoutParams(Context c, AttributeSet attrs) {
            super(c, attrs);

            final TypedArray a = c.obtainStyledAttributes(attrs, ATTRS);
            a.recycle();
        }

    }

    static class SavedState extends BaseSavedState {
        //实例化需要保存的参数
    	SlideState mSlideState;

        SavedState(Parcelable superState) {
            super(superState);
        }

        private SavedState(Parcel in) {
            super(in);
            try {
                mSlideState = Enum.valueOf(SlideState.class, in.readString());
            } catch (IllegalArgumentException e) {
                mSlideState = SlideState.COLLAPSED;
            }
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeString(mSlideState.toString());
        }

        public static final Parcelable.Creator<SavedState> CREATOR =
                new Parcelable.Creator<SavedState>() {
            @Override
            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in);
            }

            @Override
            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };
    }
}
