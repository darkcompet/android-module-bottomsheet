package tool.compet.bottomsheet;

public interface TheOnSheetDismissedListener {
	/**
	 * Called when the presented sheet has been dismissed.
	 *
	 * @param bottomSheetLayout The bottom sheet which contained the presented sheet.
	 */
	void onDismissed(MyBackgroundLayout bottomSheetLayout);
}
