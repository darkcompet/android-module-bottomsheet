package tool.compet.bottomsheet;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import androidx.databinding.ViewDataBinding;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelStoreOwner;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import tool.compet.core.DkLogcats;
import tool.compet.core.TheApp;
import tool.compet.core.TheFragment;
import tool.compet.core.DkUtils;
import tool.compet.floatingbar.DkSnackbar;
import tool.compet.floatingbar.DkToastbar;
import tool.compet.navigation.DkFragmentNavigator;
import tool.compet.navigation.DkNavigatorOwner;
import tool.compet.topic.DkTopicManager;
import tool.compet.topic.DkTopicProvider;
import tool.compet.topic.TheTopic;

public abstract class DkBottomSheetDialogFragment<B extends ViewDataBinding>
	extends BottomSheetDialogFragment
	implements TheFragment, DkNavigatorOwner, DkTopicProvider {

	/**
	 * Allow init child views via databinding feature.
	 * So we can access to child views via `binder.*` instead of calling findViewById().
	 */
	protected boolean enableDataBinding() {
		return true;
	}

	@Override
	public int fragmentContainerId() {
		return 0;
	}

	// Current application
	protected TheApp app;

	// Current fragment activity
	protected FragmentActivity host;

	// Current context
	protected Context context;

	// Layout of this view (normally is ViewGroup, but sometime, user maybe layout with single view)
	protected View layout;

	// Binder for databinding (to initialize child views instead of findViewById())
	public B binder;

	@Override
	public void onAttach(@NonNull Context context) {
		if (BuildConfig.DEBUG) {
			DkLogcats.info(this, "onAttach (context)");
		}
		if (this.host == null) {
			this.host = getActivity();
		}
		if (this.context == null) {
			this.context = context;
		}
		if (this.app == null && this.host != null) {
			this.app = (TheApp) this.host.getApplication();
		}

		super.onAttach(context);
	}

	@Override
	@SuppressWarnings("deprecation") // still work on old OS
	public void onAttach(@NonNull Activity activity) {
		if (BuildConfig.DEBUG) {
			DkLogcats.info(this, "onAttach (activity)");
		}
		if (this.context == null) {
			this.context = getContext();
		}
		if (this.host == null) {
			this.host = (FragmentActivity) activity;
		}
		if (this.app == null) {
			this.app = (TheApp) activity.getApplication();
		}

		super.onAttach(activity);
	}

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		if (BuildConfig.DEBUG) {
			DkLogcats.info(this, "onCreate");
		}
		super.onCreate(savedInstanceState);
	}

	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
		if (BuildConfig.DEBUG) {
			DkLogcats.info(this, "onCreateView");
		}

		int layoutId = layoutResourceId();
		if (layoutId > 0) {
			if (enableDataBinding()) {
				// Pass `false` to indicate don't attach this layout to parent
				this.binder = DataBindingUtil.inflate(inflater, layoutId, container, false);
				this.layout = this.binder.getRoot();
			}
			else {
				// Pass `false` to indicate don't attach this layout to parent
				this.layout = inflater.inflate(layoutId, container, false);
			}
		}
		else {
			DkLogcats.notice(this, "Fragment %s has no layout?", getClass().getName());
		}

		return this.layout;
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		if (BuildConfig.DEBUG) {
			DkLogcats.info(this, "onViewCreated");
		}
		super.onViewCreated(view, savedInstanceState);
	}

	@Override // onViewCreated() -> onViewStateRestored() -> onStart()
	public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
		if (BuildConfig.DEBUG) {
			DkLogcats.info(this, "onViewStateRestored");
		}
		if (childNavigator != null) {
			childNavigator.restoreInstanceState(savedInstanceState);
		}
		super.onViewStateRestored(savedInstanceState);
	}

	@Override
	public void onStart() {
		if (BuildConfig.DEBUG) {
			DkLogcats.info(this, "onStart");
		}
		super.onStart();
	}

	@Override
	public void onResume() {
		if (BuildConfig.DEBUG) {
			DkLogcats.info(this, "onResume");
		}
		super.onResume();
	}

	@Override
	public void onPause() {
		if (BuildConfig.DEBUG) {
			DkLogcats.info(this, "onPause");
		}
		super.onPause();
	}

	@Override
	public void onStop() {
		if (BuildConfig.DEBUG) {
			DkLogcats.info(this, "onStop");
		}
		super.onStop();
	}

	@Override
	public void onDestroyView() {
		if (BuildConfig.DEBUG) {
			DkLogcats.info(this, "onDestroyView");
		}
		super.onDestroyView();
	}

	@Override // called before onDestroy()
	public void onSaveInstanceState(@NonNull Bundle outState) {
		if (BuildConfig.DEBUG) {
			DkLogcats.info(this, "onSaveInstanceState");
		}
		if (childNavigator != null) {
			childNavigator.storeInstanceState(outState);
		}
		super.onSaveInstanceState(outState);
	}

	@Override
	public void onDestroy() {
		if (BuildConfig.DEBUG) {
			DkLogcats.info(this, "onDestroy");
		}
		super.onDestroy();
	}

	@Override
	public void onDetach() {
		if (BuildConfig.DEBUG) {
			DkLogcats.info(this, "onDetach");
		}

		this.host = null;
		this.context = null;

		super.onDetach();
	}

	@Override
	public void onLowMemory() {
		if (BuildConfig.DEBUG) {
			DkLogcats.info(this, "onLowMemory");
		}
		super.onLowMemory();
	}

	@Override
	public Fragment getFragment() {
		return this;
	}

	// Child navigator
	protected DkFragmentNavigator childNavigator;

	/**
	 * Called when user pressed to physical back button, this is normally passed from current activity.
	 * When this view got an event, this send signal to children first, if no child was found,
	 * then this will call `close()` on it to dismiss itself.
	 *
	 * @return true if this view or child of it has dismissed successfully, otherwise false.
	 */
	@Override
	public boolean onBackPressed() {
		if (childNavigator == null || childNavigator.childCount() == 0) {
			return this.close();
		}
		return childNavigator.handleOnBackPressed();
	}

	/**
	 * Open dialog via parent navigator.
	 */
	public boolean open(DkFragmentNavigator navigator) {
		return navigator.beginTransaction().add(this).commit();
	}

	/**
	 * Open dialog via parent navigator.
	 */
	public boolean open(DkFragmentNavigator navigator, int enterAnimRes, int exitAnimRes) {
		return navigator.beginTransaction().setAnims(enterAnimRes, exitAnimRes).add(this).commit();
	}

	/**
	 * Close this view by tell parent navigator remove this.
	 */
	@Override // from `DkFragment`
	public boolean close() {
		try {
			// Multiple times of calling `getParentNavigator()` maybe cause exception
			return getParentNavigator().beginTransaction().remove(this).commit();
		}
		catch (Exception e) {
			DkLogcats.error(this, e);
			return false;
		}
	}

	// region Navigator

	/**
	 * Must provide id of fragent container via `fragmentContainerId()`.
	 */
	@Override // from `DkNavigatorOwner`
	public DkFragmentNavigator getChildNavigator() {
		if (childNavigator == null) {
			int containerId = fragmentContainerId();

			if (containerId <= 0) {
				DkUtils.complainAt(this, "Must provide `fragmentContainerId()`");
			}

			childNavigator = new DkFragmentNavigator(containerId, getChildFragmentManager());
		}
		return childNavigator;
	}

	@Override // from `DkNavigatorOwner`
	public DkFragmentNavigator getParentNavigator() {
		Fragment parent = getParentFragment();
		DkFragmentNavigator parentNavigator = null;

		if (parent == null) {
			if (host instanceof DkNavigatorOwner) {
				parentNavigator = ((DkNavigatorOwner) host).getChildNavigator();
			}
		}
		else if (parent instanceof DkNavigatorOwner) {
			parentNavigator = ((DkNavigatorOwner) parent).getChildNavigator();
		}

		if (parentNavigator == null) {
			DkUtils.complainAt(this, "Must have a parent navigator own this fragment `%s`", getClass().getName());
		}

		return parentNavigator;
	}

	// endregion Navigator

	// region ViewModel

	// Get or Create new ViewModel instance which be owned by this Fragment.
	public <VM extends ViewModel> VM obtainOwnViewModel(String key, Class<VM> modelType) {
		return new ViewModelProvider(this).get(key, modelType);
	}

	// Get or Create new ViewModel instance which be owned by Activity which hosts this Fragment.
	public <VM extends ViewModel> VM obtainHostViewModel(String key, Class<VM> modelType) {
		return new ViewModelProvider(host).get(key, modelType);
	}

	// Get or Create new ViewModel instance which be owned by current app.
	public <VM extends ViewModel> VM obtainAppViewModel(String key, Class<VM> modelType) {
		Application app = host.getApplication();

		if (app instanceof ViewModelStoreOwner) {
			return new ViewModelProvider((ViewModelStoreOwner) app).get(key, modelType);
		}

		throw new RuntimeException("App must be subclass of ViewModelStoreOwner");
	}

	// endregion ViewModel

	// region Scoped topic

	/**
	 * Obtain and Join to topic under `host` scope.
	 *
	 * @param topicType Topic type in the scope, for eg,. PromotionTopic.class,...
	 */
	@Override
	public <T extends TheTopic<?>> T topic(String topicId, Class<T> topicType) {
		return new DkTopicManager<>(host, topicId, topicType).registerClient(this, false);
	}

	/**
	 * Obtain and Join to topic inside given scope.
	 *
	 * @param topicType Topic type in the scope, for eg,. PromotionTopic.class,...
	 * @param scope Where to host the topic. For eg,. `app`, `host`, `this`,...
	 */
	@Override
	public <T extends TheTopic<?>> T topic(String topicId, Class<T> topicType, ViewModelStoreOwner scope) {
		return new DkTopicManager<>(scope, topicId, topicType).registerClient(this, false);
	}

	// endregion Scoped topic

	// region Utility

	public DkSnackbar snackbar() {
		return DkSnackbar.newIns(layout);
	}

	public DkToastbar toastbar() {
		return DkToastbar.newIns(layout);
	}

	// endregion Utility
}
