package tool.compet.bottomsheet;

import android.view.View;

/**
 * A very simple view transformer which only handles applying a correct dim.
 */
abstract class MyBaseViewTransformer implements MyViewTransformer {
	public static final float MAX_DIM_ALPHA = 0.7f;

	@Override
	public float getDimAlpha(float translation, float maxTranslation, float peekedTranslation, MyBackgroundLayout parent, View view) {
		float progress = translation / maxTranslation;
		return progress * MAX_DIM_ALPHA;
	}
}
