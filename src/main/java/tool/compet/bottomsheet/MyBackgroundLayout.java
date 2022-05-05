package tool.compet.bottomsheet;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.Rect;
import android.util.Property;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import java.util.concurrent.CopyOnWriteArraySet;

import tool.compet.core.DkLogcats;

public class MyBackgroundLayout extends FrameLayout {
	public enum State {
		// Initial state
		HIDDEN,

		// Before animating
		PREPARING,

		// This is normal usecase, the sheet was displayed half (not full)
		PEEKED,

		// Displayed
		EXPANDED
	}

	public interface OnSheetStateChangeListener {
		void onSheetStateChanged(State state);
	}

	// Animation state
	private State state = State.HIDDEN;

	private static final Property<MyBackgroundLayout, Float> SHEET_TRANSLATION = new Property<>(Float.class, "sheetTranslation") {
		@Override
		public Float get(MyBackgroundLayout object) {
			return object.sheetTranslation;
		}

		@Override
		public void set(MyBackgroundLayout object, Float value) {
			object.translateSheetView(value);
		}
	};
	private static final long ANIMATION_DURATION = 250;

	// Content of sheetView should be wrapped for tablet, and matched parent for mobile.
	private final boolean isTablet = false;//getResources().getBoolean(R.bool.bottomsheet_is_tablet);

	private final int defaultSheetWidth = 0;//getResources().getDimensionPixelSize(R.dimen.bottomsheet_default_sheet_width);
	public boolean bottomSheetOwnsTouch;
	private Runnable onPostDismiss;

	// Use for what???
	private final Rect contentClipRect = new Rect();

	boolean peekOnDismiss = false;
	private final TimeInterpolator animationInterpolator = new DecelerateInterpolator(1.6f);
	private boolean sheetViewOwnsTouch;
	private float sheetTranslation;

	// For fly gesture
	private VelocityTracker velocityTracker;
	private float minFlingVelocity;
	private float touchSlop;

	// Transfomers when translation of sheetView change.
	private MyViewTransformer defaultViewTransformer = new IdentityViewTransformer();
	private MyViewTransformer viewTransformer = defaultViewTransformer;

	private boolean shouldDimContentView = true;
	boolean useHardwareLayerWhileAnimating = true;
	private Animator currentAnimator;
	final CopyOnWriteArraySet<TheOnSheetDismissedListener> onSheetDismissedListeners = new CopyOnWriteArraySet<>();
	final CopyOnWriteArraySet<OnSheetStateChangeListener> onSheetStateChangeListeners = new CopyOnWriteArraySet<>();
	private OnLayoutChangeListener sheetViewOnLayoutChangeListener;

	// Show under the `sheetView`
	private final View dimView;
	private View sheetView;

	boolean interceptContentTouch = true;
	private int currentSheetViewHeight;
	private boolean hasIntercepted;
	private final float defaultPeekKeyline;
	private float peekHeight;

	/**
	 * Some values we need to manage width on tablets
	 */
	private int screenWidth = 0;
	private int sheetStartX = 0;
	private int sheetEndX = 0;

	// Snapshot of the touch's y position on a down event
	private float downY;

	// Snapshot of the touch's x position on a down event
	private float downX;

	// Snapshot of the sheet's translation at the time of the last down event
	private float downSheetTranslation;

	// Snapshot of the sheet's state at the time of the last down event
	private State downState;

	public MyBackgroundLayout(Context context) {
		super(context);

		ViewConfiguration viewConfiguration = ViewConfiguration.get(getContext());
		this.minFlingVelocity = viewConfiguration.getScaledMinimumFlingVelocity();
		this.touchSlop = viewConfiguration.getScaledTouchSlop();

		final View dimView = this.dimView = new View(getContext());
		dimView.setBackgroundColor(Color.BLACK);
		dimView.setAlpha(0);
		dimView.setVisibility(INVISIBLE);

		setFocusableInTouchMode(true);

		Point point = new Point();
		((WindowManager) context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getSize(point);
		this.screenWidth = point.x;
		this.sheetEndX = screenWidth;

		this.peekHeight = 0; // getHeight() return 0 at start!
		this.defaultPeekKeyline = point.y - (this.screenWidth / (16.0f / 9.0f));
	}

	@Override
	protected void onAttachedToWindow() {
		super.onAttachedToWindow();
		velocityTracker = VelocityTracker.obtain();
	}

	@Override
	protected void onDetachedFromWindow() {
		super.onDetachedFromWindow();
		velocityTracker.clear();
		cancelCurrentAnimation();
	}

	@Override
	protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
		super.onLayout(changed, left, top, right, bottom);

		//todo replace getWidth() and getHeight() with right-left, bottom-top
		DkLogcats.debug(this, "---- onLayout: %d, %d, %d, %d and size: %d, %d", left, top, right, bottom, getWidth(), getHeight());

		this.contentClipRect.set(
			0,
			0,
			getWidth(),
			(int) (getHeight() - Math.ceil(this.sheetTranslation))
		);
	}

	@Override
	public boolean onKeyPreIme(int keyCode, @NonNull KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK && isSheetShowing()) {
			switch (event.getAction()) {
				case KeyEvent.ACTION_DOWN: {
					if (event.getRepeatCount() == 0) {
						KeyEvent.DispatcherState state = getKeyDispatcherState();
						if (state != null) {
							state.startTracking(event, this);
						}
						return true;
					}
				}
				case KeyEvent.ACTION_UP: {
					KeyEvent.DispatcherState dispatcherState = getKeyDispatcherState();
					if (dispatcherState != null) {
						dispatcherState.handleUpEvent(event);
					}
					if (isSheetShowing() && event.isTracking() && !event.isCanceled()) {
						if (this.state == State.EXPANDED && this.peekOnDismiss) {
							animatePeekSheet();
						}
						else {
							animateDismissSheet();
						}
						return true;
					}
				}
			}
		}
		return super.onKeyPreIme(keyCode, event);
	}

	@Override
	public boolean onInterceptTouchEvent(@NonNull MotionEvent event) {
		final boolean downAction = event.getActionMasked() == MotionEvent.ACTION_DOWN;
		if (downAction) {
			this.hasIntercepted = false;
		}
		if (interceptContentTouch || (event.getY() > getHeight() - sheetTranslation && isXInSheet(event.getX()))) {
			this.hasIntercepted = downAction && isSheetShowing();
		}
		else {
			this.hasIntercepted = false;
		}
		return this.hasIntercepted;
	}

	@Override
	public boolean onTouchEvent(@NonNull MotionEvent event) {
		if (! isSheetShowing() || isAnimating()) {
			return false;
		}
		if (! hasIntercepted) {
			return onInterceptTouchEvent(event);
		}

		final int action = event.getAction();

		if (action == MotionEvent.ACTION_DOWN) {
			// Snapshot the state of things when finger touches the screen.
			// This allows us to calculate deltas without losing precision
			// which we would have if we calculated deltas based on the previous touch.
			bottomSheetOwnsTouch = false;
			sheetViewOwnsTouch = false;
			downY = event.getY();
			downX = event.getX();
			downSheetTranslation = sheetTranslation;
			downState = state;
			velocityTracker.clear();
		}
		velocityTracker.addMovement(event);

		// The max translation is a hard limit while the min translation is where we start dragging more slowly and allow the sheet to be dismissed.
		float maxSheetTranslation = getMaxSheetTranslation();
		float peekSheetTranslation = getPeekSheetTranslation();

		float deltaY = downY - event.getY();
		float deltaX = downX - event.getX();

		if (! bottomSheetOwnsTouch && ! sheetViewOwnsTouch) {
			bottomSheetOwnsTouch = Math.abs(deltaY) > touchSlop;
			sheetViewOwnsTouch = Math.abs(deltaX) > touchSlop;

			if (bottomSheetOwnsTouch) {
				if (state == State.PEEKED) {
					MotionEvent cancelEvent = MotionEvent.obtain(event);
					cancelEvent.offsetLocation(0, sheetTranslation - getHeight());
					cancelEvent.setAction(MotionEvent.ACTION_CANCEL);
					getSheetView().dispatchTouchEvent(cancelEvent);
					cancelEvent.recycle();
				}

				sheetViewOwnsTouch = false;
				downY = event.getY();
				downX = event.getX();
				deltaY = 0;
				deltaX = 0;
			}
		}

		// This is not the actual new sheet translation but a first approximation it will be adjusted to account for max and min translations etc.
		float newSheetTranslation = downSheetTranslation + deltaY;

		if (bottomSheetOwnsTouch) {
			// If we are scrolling down and the sheet cannot scroll further, go out of expanded mode.
			boolean scrollingDown = deltaY < 0;
			boolean canScrollUp = canScrollUp(getSheetView(), event.getX(), event.getY() + (sheetTranslation - getHeight()));
			if (state == State.EXPANDED && scrollingDown && !canScrollUp) {
				// Reset variables so deltas are correctly calculated from the point at which the sheet was 'detached' from the top.
				downY = event.getY();
				downSheetTranslation = sheetTranslation;
				velocityTracker.clear();
				setState(State.PEEKED);
				setSheetLayerTypeIfEnabled(LAYER_TYPE_HARDWARE);
				newSheetTranslation = sheetTranslation;

				// Dispatch a cancel event to the sheet to make sure its touch handling is cleaned up nicely.
				MotionEvent cancelEvent = MotionEvent.obtain(event);
				cancelEvent.setAction(MotionEvent.ACTION_CANCEL);
				getSheetView().dispatchTouchEvent(cancelEvent);
				cancelEvent.recycle();
			}

			// If we are at the top of the view we should go into expanded mode.
			if (state == State.PEEKED && newSheetTranslation > maxSheetTranslation) {
				translateSheetView(maxSheetTranslation);

				// Dispatch a down event to the sheet to make sure its touch handling is initiated correctly.
				newSheetTranslation = Math.min(maxSheetTranslation, newSheetTranslation);
				MotionEvent downEvent = MotionEvent.obtain(event);
				downEvent.setAction(MotionEvent.ACTION_DOWN);
				getSheetView().dispatchTouchEvent(downEvent);
				downEvent.recycle();

				setState(State.EXPANDED);
				setSheetLayerTypeIfEnabled(View.LAYER_TYPE_NONE);
			}

			if (state == State.EXPANDED) {
				// Dispatch the touch to the sheet if we are expanded so it can handle its own internal scrolling.
				event.offsetLocation(0, sheetTranslation - getHeight());
				getSheetView().dispatchTouchEvent(event);
			}
			else {
				// Make delta less effective when sheet is below the minimum translation.
				// This makes it feel like scrolling in jello which gives the user an indication that the sheet will be dismissed if they let go.
				if (newSheetTranslation < peekSheetTranslation) {
					newSheetTranslation = peekSheetTranslation - (peekSheetTranslation - newSheetTranslation) / 4f;
				}

				translateSheetView(newSheetTranslation);

				if (action == MotionEvent.ACTION_CANCEL) {
					// If touch is canceled, go back to previous state, a canceled touch should never commit an action.
					if (downState == State.EXPANDED) {
						animateExpandSheet();
					}
					else {
						animatePeekSheet();
					}
				}

				if (action == MotionEvent.ACTION_UP) {
					if (newSheetTranslation < peekSheetTranslation) {
						animateDismissSheet();
					}
					else {
						// If touch is released, go to a new state depending on velocity.
						// If the velocity is not high enough we use the position of the sheet to determine the new state.
						velocityTracker.computeCurrentVelocity(1000);
						float velocityY = velocityTracker.getYVelocity();
						if (Math.abs(velocityY) < minFlingVelocity) {
							if (sheetTranslation > getHeight() / 2f) {
								animateExpandSheet();
							}
							else {
								animatePeekSheet();
							}
						}
						else {
							if (velocityY < 0) {
								animateExpandSheet();
							}
							else {
								animatePeekSheet();
							}
						}
					}
				}
			}
		}
		else {
			// If the user clicks outside of the bottom sheet area we should dismiss the bottom sheet.
			boolean touchOutsideBottomSheet = event.getY() < getHeight() - sheetTranslation || ! isXInSheet(event.getX());
			if (action == MotionEvent.ACTION_UP && touchOutsideBottomSheet && interceptContentTouch) {
				animateDismissSheet();
				return true;
			}

			event.offsetLocation(isTablet ? getX() - sheetStartX : 0, sheetTranslation - getHeight());
			getSheetView().dispatchTouchEvent(event);
		}
		return true;
	}

	/**
	 * Convenience for showWithSheetView(sheetView, null, null).
	 *
	 * @param sheetView The sheet to be presented.
	 */
	void animateShowSheet(View sheetView) {
		animateShowSheet(sheetView, null);
	}

	/**
	 * Present a sheet view to the user.
	 * If another sheet is currently presented, it will be dismissed, and the new sheet will be shown after that.
	 *
	 * @param sheetView The sheet to be presented.
	 * @param viewTransformer The view transformer to use when presenting the sheet.
	 */
	void animateShowSheet(final View sheetView, final MyViewTransformer viewTransformer) {
		// Make sure sheet is not animating.
		// If it is animating, we animate after dismissed.
		if (this.state != State.HIDDEN) {
			final Runnable runAfterDismiss = () -> animateShowSheet(sheetView, viewTransformer);
			animateDismissSheet(runAfterDismiss);
			return;
		}
		setState(State.PREPARING);

		// Prepare to add content view to this layout.
		// We will add `dimView` first, then add `sheetView` over on.
		LayoutParams sheetViewLayoutParams = (LayoutParams) sheetView.getLayoutParams();
		if (sheetViewLayoutParams == null) {
			sheetViewLayoutParams = new LayoutParams(
				this.isTablet ? LayoutParams.WRAP_CONTENT : LayoutParams.MATCH_PARENT,
				LayoutParams.WRAP_CONTENT,
				Gravity.CENTER_HORIZONTAL
			);
		}

		if (this.isTablet && sheetViewLayoutParams.width == LayoutParams.WRAP_CONTENT) {
			// Center by default if they didn't specify anything
			if (sheetViewLayoutParams.gravity == LayoutParams.MATCH_PARENT) {
				sheetViewLayoutParams.gravity = Gravity.CENTER_HORIZONTAL;
			}

			sheetViewLayoutParams.width = this.defaultSheetWidth;

			// Update start and end coordinates for touch reference
			int horizontalSpacing = this.screenWidth - this.defaultSheetWidth;
			this.sheetStartX = horizontalSpacing / 2;
			this.sheetEndX = this.screenWidth - this.sheetStartX;
		}

		// Add `dimView -> sheetView` at top of this layout (index = -1)
		super.addView(this.dimView, -1, generateDefaultLayoutParams()); // match parent
		super.addView(sheetView, -1, sheetViewLayoutParams); // custom params

		initializeSheetValues();

		this.viewTransformer = viewTransformer;

		// Don't start animating until the sheet has been drawn once.
		// This ensures that we don't do layout while animating and that
		// the drawing cache for the view has been warmed up. tl;dr it reduces lag.
		getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
			@Override
			public boolean onPreDraw() {
				getViewTreeObserver().removeOnPreDrawListener(this); // this: ViewTreeObserver.OnPreDrawListener
				post(() -> {
					// Make sure sheet view is still here when first draw happens.
					// In the case of a large lag it could be that the view is dismissed before
					// it is drawn resulting in sheet view being null here.
					if (getSheetView() != null) {
						animatePeekSheet();
					}
				});
				return true;
			}
		});

		// SheetView should always be anchored to the bottom of the screen.
		// Translate sheetViee when layout changed
		this.currentSheetViewHeight = sheetView.getMeasuredHeight();
		this.sheetViewOnLayoutChangeListener = (theSheetView, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
			DkLogcats.debug(this, "----- sheetViewOnLayoutChangeListener has happend");

			final int newSheetViewHeight = theSheetView.getMeasuredHeight();
			if (this.state != State.HIDDEN) {
				// The sheet can no longer be in the expanded state if it has shrunk
				if (newSheetViewHeight < this.currentSheetViewHeight) {
					if (this.state == State.EXPANDED) {
						setState(State.PEEKED);
					}
					translateSheetView(newSheetViewHeight);
				}
				else if (this.currentSheetViewHeight > 0 && newSheetViewHeight > this.currentSheetViewHeight && this.state == State.PEEKED) {
					if (newSheetViewHeight == getMaxSheetTranslation()) {
						setState(State.EXPANDED);
					}
					translateSheetView(newSheetViewHeight);
				}
			}
			this.currentSheetViewHeight = newSheetViewHeight;
		};

		sheetView.addOnLayoutChangeListener(this.sheetViewOnLayoutChangeListener);
	}

	/**
	 * Dismiss the sheet currently being presented.
	 */
	void animateDismissSheet() {
		animateDismissSheet(null);
	}

	private void animateDismissSheet(Runnable onPostDismiss) {
		if (state == State.HIDDEN) {
			this.onPostDismiss = null;
			return;
		}
		// This must be set every time, including if the parameter is null
		// Otherwise a new sheet might be shown when the caller called dismiss after a showWithSheet call, which would be
		this.onPostDismiss = onPostDismiss;
		final View sheetView = getSheetView();
		sheetView.removeOnLayoutChangeListener(sheetViewOnLayoutChangeListener);

		cancelCurrentAnimation();

		final ObjectAnimator animator = ObjectAnimator.ofFloat(this, SHEET_TRANSLATION, 0);
		animator.setDuration(ANIMATION_DURATION);
		animator.setInterpolator(animationInterpolator);
		animator.addListener(new CancelDetectionAnimationListener() {
			@Override
			public void onAnimationEnd(Animator animation) {
				if (! canceled) {
					currentAnimator = null;
					setState(State.HIDDEN);
					setSheetLayerTypeIfEnabled(LAYER_TYPE_NONE);

					// Remove sheetView
					//todo remove dimView too?
					removeView(sheetView);

					for (TheOnSheetDismissedListener onSheetDismissedListener : onSheetDismissedListeners) {
						onSheetDismissedListener.onDismissed(MyBackgroundLayout.this);
					}

					// Remove sheet specific properties
					viewTransformer = null;
					if (MyBackgroundLayout.this.onPostDismiss != null) {
						MyBackgroundLayout.this.onPostDismiss.run();
						MyBackgroundLayout.this.onPostDismiss = null;
					}
				}
			}
		});
		animator.start();

		currentAnimator = animator;
		sheetStartX = 0;
		sheetEndX = screenWidth;
	}

	private boolean isXInSheet(float x) {
		return ! isTablet || (x >= sheetStartX && x <= sheetEndX);
	}

	private boolean isAnimating() {
		return currentAnimator != null;
	}

	private void cancelCurrentAnimation() {
		if (currentAnimator != null) {
			currentAnimator.cancel();
		}
	}

	private boolean canScrollUp(View view, float x, float y) {
		if (view instanceof ViewGroup) {
			final ViewGroup layout = (ViewGroup) view;

			for (int index = layout.getChildCount() - 1; index >= 0; --index) {
				final View child = layout.getChildAt(index);
				final int childLeft = child.getLeft() - view.getScrollX();
				final int childTop = child.getTop() - view.getScrollY();
				final int childRight = child.getRight() - view.getScrollX();
				final int childBottom = child.getBottom() - view.getScrollY();

				final boolean intersects = x > childLeft && x < childRight && y > childTop && y < childBottom;

				if (intersects && canScrollUp(child, x - childLeft, y - childTop)) {
					return true;
				}
			}
		}
		return view.canScrollVertically(-1);
	}

	private void setSheetLayerTypeIfEnabled(int layerType) {
		if (useHardwareLayerWhileAnimating) {
			getSheetView().setLayerType(layerType, null);
		}
	}

	private boolean hasTallerKeylineHeightSheet() {
		return getSheetView() == null || getSheetView().getHeight() > defaultPeekKeyline;
	}

	/**
	 * Set dim and translation to the initial state
	 */
	private void initializeSheetValues() {
		this.sheetTranslation = 0;
		this.contentClipRect.set(0, 0, getWidth(), getHeight());

		getSheetView().setTranslationY(getHeight());

		this.dimView.setAlpha(0);
		this.dimView.setVisibility(INVISIBLE);
	}

	/**
	 * Set the presented sheet to be in an expanded state.
	 */
	private void animateExpandSheet() {
		cancelCurrentAnimation();
		setSheetLayerTypeIfEnabled(LAYER_TYPE_NONE);

		ObjectAnimator anim = ObjectAnimator.ofFloat(this, SHEET_TRANSLATION, getMaxSheetTranslation());
		anim.setDuration(ANIMATION_DURATION);
		anim.setInterpolator(animationInterpolator);
		anim.addListener(new CancelDetectionAnimationListener() {
			@Override
			public void onAnimationEnd(@NonNull Animator animation) {
				if (!canceled) {
					currentAnimator = null;
				}
			}
		});
		anim.start();

		currentAnimator = anim;
		setState(State.EXPANDED);
	}

	/**
	 * Set the presented sheet to be in a peeked state.
	 */
	public void animatePeekSheet() {
		cancelCurrentAnimation();
		setSheetLayerTypeIfEnabled(LAYER_TYPE_HARDWARE);

		ObjectAnimator anim = ObjectAnimator.ofFloat(this, SHEET_TRANSLATION, getPeekSheetTranslation());
		anim.setDuration(ANIMATION_DURATION);
		anim.setInterpolator(animationInterpolator);
		anim.addListener(new CancelDetectionAnimationListener() {
			@Override
			public void onAnimationEnd(@NonNull Animator animation) {
				if (! canceled) {
					currentAnimator = null;
				}
			}
		});
		anim.start();

		currentAnimator = anim;
		setState(State.PEEKED);
	}

	/**
	 * @return The peeked state translation for the presented sheet view. Translation is counted from the bottom of the view.
	 */
	public float getPeekSheetTranslation() {
		return peekHeight == 0 ? getDefaultPeekTranslation() : peekHeight;
	}

	/**
	 * Set custom height for PEEKED state.
	 *
	 * @param peekHeight Peek height in pixels
	 */
	public void setPeekSheetTranslation(float peekHeight) {
		this.peekHeight = peekHeight;
	}

	private float getDefaultPeekTranslation() {
		return hasTallerKeylineHeightSheet() ? defaultPeekKeyline : getSheetView().getHeight();
	}

	/**
	 * @return The currently presented sheet view. If no sheet is currently presented null will returned.
	 */
	private View getContentView() {
		return getChildCount() > 0 ? getChildAt(0) : null;
	}

	/**
	 * @return The currently presented sheet view. If no sheet is currently presented null will returned.
	 */
	private View getSheetView() {
		return getChildCount() > 1 ? getChildAt(1) : null;
	}

	private void setState(State state) {
		if (state != this.state) {
			this.state = state;

			for (OnSheetStateChangeListener listener : onSheetStateChangeListeners) {
				listener.onSheetStateChanged(state);
			}
		}
	}

	/**
	 * @return Whether or not a sheet is currently presented.
	 */
	public boolean isSheetShowing() {
		return this.state != State.HIDDEN;
	}

	/**
	 * Set the default view transformer to use for showing a sheet. Usually applications will use
	 * a similar transformer for most use cases of bottom sheet so this is a convenience instead of
	 * passing a new transformer each time a sheet is shown. This choice is overridden by any
	 * view transformer passed to showWithSheetView().
	 *
	 * @param defaultViewTransformer The view transformer user by default.
	 */
	public void setDefaultViewTransformer(MyViewTransformer defaultViewTransformer) {
		this.defaultViewTransformer = defaultViewTransformer;
	}

	/**
	 * Enable or disable dimming of the content view while a sheet is presented. If enabled a
	 * transparent black dim is overlaid on top of the content view indicating that the sheet is the
	 * foreground view. This dim is animated into place is coordination with the sheet view.
	 * Defaults to true.
	 *
	 * @param shouldDimContentView whether or not to dim the content view.
	 */
	public void setShouldDimContentView(boolean shouldDimContentView) {
		this.shouldDimContentView = shouldDimContentView;
	}

	/**
	 * @return whether the content view is being dimmed while presenting a sheet or not.
	 */
	public boolean shouldDimContentView() {
		return shouldDimContentView;
	}

	private void translateSheetView(float newTranslation) {
		final float sheetTranslationY = this.sheetTranslation = Math.min(newTranslation, getMaxSheetTranslation());
		final int bottomClip = (int) (getHeight() - Math.ceil(sheetTranslationY));

		this.contentClipRect.set(0, 0, getWidth(), bottomClip);

		getSheetView().setTranslationY(getHeight() - sheetTranslationY);
		onSheetTranslated(sheetTranslationY);

		if (this.shouldDimContentView) {
			final float dimAlpha = getDimAlpha(sheetTranslationY);
			final View dimView = this.dimView;
			dimView.setAlpha(dimAlpha);
			dimView.setVisibility(dimAlpha > 0 ? VISIBLE : INVISIBLE);
		}
	}

	/**
	 * @return The maximum translation for the presented sheet view. Translation is counted from the bottom of the view.
	 */
	private float getMaxSheetTranslation() {
		return hasFullHeightSheet() ? getHeight() - getPaddingTop() : getSheetView().getHeight();
	}

	private boolean hasFullHeightSheet() {
		return getSheetView() == null || getSheetView().getHeight() == getHeight();
	}

	// Let listeners know the change of sheet
	private void onSheetTranslated(float sheetTranslation) {
		if (viewTransformer != null) {
			viewTransformer.transformView(sheetTranslation, getMaxSheetTranslation(), getPeekSheetTranslation(), this, getContentView());
		}
		else if (defaultViewTransformer != null) {
			defaultViewTransformer.transformView(sheetTranslation, getMaxSheetTranslation(), getPeekSheetTranslation(), this, getContentView());
		}
	}

	private float getDimAlpha(float sheetTranslation) {
		if (viewTransformer != null) {
			return viewTransformer.getDimAlpha(
				sheetTranslation,
				getMaxSheetTranslation(),
				getPeekSheetTranslation(),
				this,
				getContentView()
			);
		}
		else if (defaultViewTransformer != null) {
			return defaultViewTransformer.getDimAlpha(
				sheetTranslation,
				getMaxSheetTranslation(),
				getPeekSheetTranslation(),
				this,
				getContentView()
			);
		}
		return 0;
	}

	/**
	 * Returns whether or not BottomSheetLayout will assume it's being shown on a tablet.
	 *
	 * @param context Context instance to retrieve resources
	 * @return True if BottomSheetLayout will assume it's being shown on a tablet, false if not
	 */
	public static boolean isTablet(Context context) {
		return false;//context.getResources().getBoolean(R.bool.bottomsheet_is_tablet);
	}

	/**
	 * Returns the predicted default width of the sheet if it were shown.
	 *
	 * @param context Context instance to retrieve resources and display metrics
	 * @return Predicted width of the sheet if shown
	 */
	public static int predictedDefaultWidth(Context context) {
		if (isTablet(context)) {
			return 0; //context.getResources().getDimensionPixelSize(R.dimen.bottomsheet_default_sheet_width);
		}
		else {
			return context.getResources().getDisplayMetrics().widthPixels;
		}
	}
}
