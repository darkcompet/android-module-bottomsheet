package tool.compet.bottomsheet;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;

/**
 * Utility class which registers if the animation has been canceled so that subclasses may respond differently in onAnimationEnd
 */
public class CancelDetectionAnimationListener extends AnimatorListenerAdapter {
	protected boolean canceled;

	@Override
	public void onAnimationCancel(Animator animation) {
		canceled = true;
	}
}