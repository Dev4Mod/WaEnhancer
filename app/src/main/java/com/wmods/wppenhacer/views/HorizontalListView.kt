package com.wmods.wppenhacer.views

import android.content.Context
import android.database.DataSetObserver
import android.graphics.Rect
import android.os.SystemClock
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewParent
import android.widget.AdapterView
import android.widget.ListAdapter
import android.widget.Scroller
import java.util.LinkedList
import java.util.Queue

open class HorizontalListView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : AdapterView<ListAdapter>(context, attrs) {

    protected var mAdapter: ListAdapter? = null
    private var mLeftViewIndex = -1
    private var mRightViewIndex = 0
    protected var mCurrentX = 0
    protected var mNextX = 0
    private var mMaxX = Int.MAX_VALUE
    private var mDisplayOffset = 0
    protected lateinit var mScroller: Scroller
    private lateinit var mGesture: GestureDetector
    private val mRemovedViewQueue: Queue<View> = LinkedList()
    private var mOnItemSelected: OnItemSelectedListener? = null
    private var mOnItemClicked: OnItemClickListener? = null
    private var mOnItemLongClicked: OnItemLongClickListener? = null
    private var mDataChanged = false

    private val mDataObserver = object : DataSetObserver() {
        override fun onChanged() {
            synchronized(this@HorizontalListView) { mDataChanged = true }
            emptyView = emptyView
            invalidate()
            requestLayout()
        }

        override fun onInvalidated() {
            reset()
            invalidate()
            requestLayout()
        }
    }

    private val mOnGesture = object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(event: MotionEvent): Boolean = this@HorizontalListView.onDown(event)

        override fun onFling(
            first: MotionEvent?,
            second: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean = this@HorizontalListView.onFling(first, second, velocityX, velocityY)

        override fun onScroll(
            first: MotionEvent?,
            second: MotionEvent,
            distanceX: Float,
            distanceY: Float
        ): Boolean {
            parent!!.requestDisallowInterceptTouchEvent(true)
            synchronized(this@HorizontalListView) { mNextX += distanceX.toInt() }
            requestLayout()
            return true
        }

        override fun onSingleTapConfirmed(event: MotionEvent): Boolean {
            val viewRect = Rect()
            for (index in 0 until childCount) {
                val child = getChildAt(index)
                viewRect.set(child.left, child.top, child.right, child.bottom)
                if (viewRect.contains(event.x.toInt(), event.y.toInt())) {
                    val itemPosition = mLeftViewIndex + 1 + index
                    mOnItemClicked?.onItemClick(
                        this@HorizontalListView,
                        child,
                        itemPosition,
                        mAdapter!!.getItemId(itemPosition)
                    )
                    mOnItemSelected?.onItemSelected(
                        this@HorizontalListView,
                        child,
                        itemPosition,
                        mAdapter!!.getItemId(itemPosition)
                    )
                    val motionEvent = MotionEvent.obtain(
                        SystemClock.uptimeMillis(),
                        SystemClock.uptimeMillis(),
                        MotionEvent.ACTION_DOWN,
                        event.x - child.left,
                        event.y - child.top,
                        0
                    )
                    child.dispatchTouchEvent(motionEvent)
                    motionEvent.recycle()
                    child.performClick()
                    break
                }
            }
            return true
        }

        override fun onLongPress(event: MotionEvent) {
            val viewRect = Rect()
            for (index in 0 until childCount) {
                val child = getChildAt(index)
                viewRect.set(child.left, child.top, child.right, child.bottom)
                if (viewRect.contains(event.x.toInt(), event.y.toInt())) {
                    val itemPosition = mLeftViewIndex + 1 + index
                    mOnItemLongClicked?.onItemLongClick(
                        this@HorizontalListView,
                        child,
                        itemPosition,
                        mAdapter!!.getItemId(itemPosition)
                    )
                    break
                }
            }
        }
    }

    init {
        initView()
    }

    private fun initView() {
        mLeftViewIndex = -1
        mRightViewIndex = 0
        mDisplayOffset = 0
        mCurrentX = 0
        mNextX = 0
        mMaxX = Int.MAX_VALUE
        mScroller = Scroller(context)
        mGesture = GestureDetector(context, mOnGesture)
    }

    override fun setOnItemSelectedListener(listener: OnItemSelectedListener?) {
        mOnItemSelected = listener
    }

    override fun setOnItemClickListener(listener: OnItemClickListener?) {
        mOnItemClicked = listener
    }

    override fun setOnItemLongClickListener(listener: OnItemLongClickListener?) {
        mOnItemLongClicked = listener
    }

    override fun getAdapter(): ListAdapter? = mAdapter

    override fun getSelectedView(): View? = null

    override fun setAdapter(adapter: ListAdapter?) {
        mAdapter?.unregisterDataSetObserver(mDataObserver)
        mAdapter = adapter
        mAdapter!!.registerDataSetObserver(mDataObserver)
        reset()
    }

    private fun reset() {
        initView()
        removeAllViewsInLayout()
        requestLayout()
    }

    override fun setSelection(position: Int) {
        // TODO: implement
    }

    private fun addAndMeasureChild(child: View, viewPosition: Int) {
        val params = child.layoutParams ?: LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        addViewInLayout(child, viewPosition, params, true)
        child.measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST)
        )
    }

    private fun fillList(dx: Int) {
        var edge = getChildAt(childCount - 1)?.right ?: 0
        fillListRight(edge, dx)

        edge = getChildAt(0)?.left ?: 0
        fillListLeft(edge, dx)
    }

    private fun fillListRight(rightEdge: Int, dx: Int) {
        var edge = rightEdge
        while (edge + dx < width && mRightViewIndex < mAdapter!!.count) {
            val child = mAdapter!!.getView(mRightViewIndex, mRemovedViewQueue.poll(), this)
            addAndMeasureChild(child, -1)
            edge += child.measuredWidth

            if (mRightViewIndex == mAdapter!!.count - 1) {
                mMaxX = mCurrentX + edge - width
            }
            if (mMaxX < 0) mMaxX = 0
            mRightViewIndex++
        }
    }

    private fun fillListLeft(leftEdge: Int, dx: Int) {
        var edge = leftEdge
        while (edge + dx > 0 && mLeftViewIndex >= 0) {
            val child = mAdapter!!.getView(mLeftViewIndex, mRemovedViewQueue.poll(), this)
            addAndMeasureChild(child, 0)
            edge -= child.measuredWidth
            mLeftViewIndex--
            mDisplayOffset -= child.measuredWidth
        }
    }

    private fun removeNonVisibleItems(dx: Int) {
        var child = getChildAt(0)
        while (child != null && child.right + dx <= 0) {
            mDisplayOffset += child.measuredWidth
            mRemovedViewQueue.offer(child)
            removeViewInLayout(child)
            mLeftViewIndex++
            child = getChildAt(0)
        }

        child = getChildAt(childCount - 1)
        while (child != null && child.left + dx >= width) {
            mRemovedViewQueue.offer(child)
            removeViewInLayout(child)
            mRightViewIndex--
            child = getChildAt(childCount - 1)
        }
    }

    private fun positionItems(dx: Int) {
        if (childCount > 0) {
            mDisplayOffset += dx
            var left = mDisplayOffset
            for (index in 0 until childCount) {
                val child = getChildAt(index)
                val childWidth = child.measuredWidth
                child.layout(left, 0, left + childWidth, child.measuredHeight)
                left += childWidth
            }
        }
    }

    @Synchronized
    fun scrollTo(x: Int) {
        mScroller.startScroll(mNextX, 0, x - mNextX, 0)
        requestLayout()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> requestParentIntercept(false)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> requestParentIntercept(true)
        }
        return mGesture.onTouchEvent(event)
    }

    private fun requestParentIntercept(allowIntercept: Boolean) {
        val parent: ViewParent? = parent
        parent?.requestDisallowInterceptTouchEvent(!allowIntercept)
    }

    protected fun onFling(
        first: MotionEvent?,
        second: MotionEvent,
        velocityX: Float,
        velocityY: Float
    ): Boolean {
        synchronized(this) {
            mScroller.fling(mNextX, 0, -velocityX.toInt(), 0, 0, mMaxX, 0, 0)
        }
        requestLayout()
        return true
    }

    protected fun onDown(event: MotionEvent): Boolean {
        mScroller.forceFinished(true)
        return true
    }

    @Synchronized
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (mAdapter == null) return

        if (mDataChanged) {
            val oldCurrentX = mCurrentX
            initView()
            removeAllViewsInLayout()
            mNextX = oldCurrentX
            mDataChanged = false
        }

        if (mScroller.computeScrollOffset()) mNextX = mScroller.currX
        if (mNextX <= 0) {
            mNextX = 0
            mScroller.forceFinished(true)
        }
        if (mNextX >= mMaxX) {
            mNextX = mMaxX
            mScroller.forceFinished(true)
        }

        val dx = mCurrentX - mNextX
        removeNonVisibleItems(dx)
        fillList(dx)
        positionItems(dx)
        mCurrentX = mNextX

        if (!mScroller.isFinished) post { requestLayout() }
    }
}
