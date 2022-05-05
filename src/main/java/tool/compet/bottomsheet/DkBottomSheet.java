package tool.compet.bottomsheet;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

/**
 * This class shows a View from bottom of the layout.
 * <pre><code>
 *    DkBottomSheet.newIns(context, layout).show(view);
 * </code></pre>
 */
public class DkBottomSheet {
	private final ViewGroup rootLayout;
	private final MyBackgroundLayout backgroundLayout;

	public DkBottomSheet(Context context, ViewGroup layout) {
		this.rootLayout = layout;
		this.backgroundLayout = new MyBackgroundLayout(context);
	}

	public static DkBottomSheet newIns(Context context, ViewGroup layout) {
		return new DkBottomSheet(context, layout);
	}

	public void showWithSheet(View sheetView) {
		final MyBackgroundLayout backgroundLayout = this.backgroundLayout;
		this.rootLayout.addView(backgroundLayout);

		backgroundLayout.animateShowSheet(sheetView);
	}

	// region Get/Set

	/**
	 * Enable or disable the use of a hardware layer for the presented sheet while animating.
	 * This settings defaults to true and should only be changed if you know that putting the
	 * sheet in a layer will negatively effect performance. One such example is if the sheet contains
	 * a view which needs to frequently be re-drawn.
	 *
	 * @param use whether or not to use a hardware layer.
	 */
	public void setUseHardwareLayerWhileAnimating(boolean use) {
		this.backgroundLayout.useHardwareLayerWhileAnimating = use;
	}

	/**
	 * Adds an {@link MyBackgroundLayout.OnSheetStateChangeListener} which will be notified when the state of the presented sheet changes.
	 * The listener will not be automatically removed, so remember to remove it when it's no longer needed
	 * (probably when the sheet is HIDDEN)
	 *
	 * @param listener the listener to be notified.
	 */
	public void addOnSheetStateChangeListener(@NonNull MyBackgroundLayout.OnSheetStateChangeListener listener) {
		this.backgroundLayout.onSheetStateChangeListeners.add(listener);
	}

	/**
	 * Adds an {@link TheOnSheetDismissedListener} which will be notified when the state of the presented sheet changes.
	 * The listener will not be automatically removed, so remember to remove it when it's no longer needed
	 * (probably when the sheet is HIDDEN)
	 *
	 * @param listener the listener to be notified.
	 */
	public void addOnSheetDismissedListener(@NonNull TheOnSheetDismissedListener listener) {
		this.backgroundLayout.onSheetDismissedListeners.add(listener);
	}

	/**
	 * Removes a previously added {@link MyBackgroundLayout.OnSheetStateChangeListener}.
	 *
	 * @param listener the listener to be removed.
	 */
	public void removeOnSheetStateChangeListener(@NonNull MyBackgroundLayout.OnSheetStateChangeListener listener) {
		this.backgroundLayout.onSheetStateChangeListeners.remove(listener);
	}

	/**
	 * Removes a previously added {@link TheOnSheetDismissedListener}.
	 *
	 * @param listener the listener to be removed.
	 */
	public void removeOnSheetDismissedListener(@NonNull TheOnSheetDismissedListener listener) {
		this.backgroundLayout.onSheetDismissedListeners.remove(listener);
	}

	/**
	 * Returns the current peekOnDismiss value, which controls the behavior response to back presses
	 * when the current state is {@link MyBackgroundLayout.State#EXPANDED}.
	 *
	 * @return the current peekOnDismiss value
	 */
	public boolean getPeekOnDismiss() {
		return this.backgroundLayout.peekOnDismiss;
	}

	/**
	 * Controls the behavior on back button press when the state is {@link MyBackgroundLayout.State#EXPANDED}.
	 *
	 * @param peekOnDismiss true to show the peeked state on back press or false to completely hide
	 *                      the Bottom Sheet. Default is false.
	 */
	public void setPeekOnDismiss(boolean peekOnDismiss) {
		this.backgroundLayout.peekOnDismiss = peekOnDismiss;
	}

	/**
	 * @return true if we are intercepting content view touches or false to allow interaction with
	 * Bottom Sheet's content view. Default value is true.
	 */
	public boolean getInterceptContentTouch() {
		return this.backgroundLayout.interceptContentTouch;
	}

	/**
	 * Controls whether or not child view interaction is possible when the bottomsheet is open.
	 *
	 * @param interceptContentTouch true to intercept content view touches or false to allow
	 *                              interaction with Bottom Sheet's content view
	 */
	public void setInterceptContentTouch(boolean interceptContentTouch) {
		this.backgroundLayout.interceptContentTouch = interceptContentTouch;
	}

	// endregion Get/Set
}
