package com.liangnie.xmap.fragments;

import android.content.Context;
import android.location.Location;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RadioGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.amap.api.services.core.LatLonPoint;
import com.amap.api.services.core.PoiItem;
import com.amap.api.services.route.BusRouteResult;
import com.amap.api.services.route.DriveRouteResult;
import com.amap.api.services.route.RideRouteResult;
import com.amap.api.services.route.RouteSearch;
import com.amap.api.services.route.WalkRouteResult;
import com.liangnie.xmap.R;
import com.liangnie.xmap.activities.MainMapActivity;
import com.liangnie.xmap.utils.ToastUtil;

public class RouteFragment extends Fragment implements View.OnClickListener,
        RadioGroup.OnCheckedChangeListener,
        AdapterView.OnItemClickListener,
        RouteSearch.OnRouteSearchListener,
        View.OnFocusChangeListener {

    private static final int MODE_DRIVE = 100;
    private static final int MODE_BUS = 101;

    private int mPlanMode;
    private String mCurrentCityCode;
    private RouteSearch mRouteSearch;
    private PoiItem mStartPoi;  // 起点
    private PoiItem mEndPoi;    // 终点

    private Fragment mCurrentFragment;  // 当前显示的Fragment
    private RouteSearchResultFragment mRouteResultFragment;
    private DriveRouteFragment mDriveRouteFragment;
    private BusRouteFragment mBusRouteFragment;

    private LinearLayout mSearchBar;
    private EditText mInputOrigin;  // 起点输入
    private EditText mInputDestination;  // 终点输入

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mPlanMode = MODE_DRIVE;
        mRouteSearch = new RouteSearch(getActivity());
        mRouteSearch.setRouteSearchListener(this);

        initFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_route, container, false);

        mSearchBar = view.findViewById(R.id.search_bar);
        mInputOrigin = view.findViewById(R.id.input_origin);
        mInputDestination = view.findViewById(R.id.input_destination);

        RadioGroup radioGroup = view.findViewById(R.id.plan_mode_rb_group);
        ImageButton btnBack = view.findViewById(R.id.btn_back); // 返回按钮
        ImageButton btnSwap = view.findViewById(R.id.btn_swap_route);   // 起点-终点交换按钮

        radioGroup.setOnCheckedChangeListener(this);
        btnBack.setOnClickListener(this);
        btnSwap.setOnClickListener(this);
        mInputOrigin.setOnClickListener(this);
        mInputDestination.setOnClickListener(this);
        mInputOrigin.setOnFocusChangeListener(this);
        mInputDestination.setOnFocusChangeListener(this);

        return view;
    }

    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
        setStartPoi(null);
        setEndPoi(null);
        unregisterListener();

        super.onViewStateRestored(savedInstanceState);
    }

    @Override
    public void onStart() {
        super.onStart();

        registerListener();
        setData();
    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterListener();
    }


    private void setData() {
        MainMapActivity activity = (MainMapActivity) getActivity();
        if (null != activity) {
            Location myLocation = activity.getMyLocation();
            mCurrentCityCode = myLocation.getExtras().getString("citycode");
            LatLonPoint point = new LatLonPoint(myLocation.getLatitude(), myLocation.getLongitude());
            if (mStartPoi == null) {
                setStartPoi(new PoiItem("", point, "我的位置", ""));
            }
        }
    }

    private void initFragment() {
        mRouteResultFragment = new RouteSearchResultFragment();
        mRouteResultFragment.setOnItemClickListener(this);

        mDriveRouteFragment = new DriveRouteFragment();
        mBusRouteFragment = new BusRouteFragment();
    }

    private void registerListener() {
        mInputOrigin.addTextChangedListener(mRouteResultFragment);
        mInputDestination.addTextChangedListener(mRouteResultFragment);
    }

    private void unregisterListener() {
        mInputOrigin.removeTextChangedListener(mRouteResultFragment);
        mInputDestination.removeTextChangedListener(mRouteResultFragment);
    }

    private void switchFragment(Fragment target) {
        FragmentTransaction transaction = getChildFragmentManager().beginTransaction();

        if (!target.isAdded()) {
            if (mCurrentFragment != null) {
                transaction.hide(mCurrentFragment).add(R.id.fragment_container, target).commit();
            } else {
                transaction.add(R.id.fragment_container, target).commit();
            }
        } else {
            transaction.hide(mCurrentFragment).show(target).commit();
        }

        mCurrentFragment = target;
    }

    public void backMainFragment() {
        MainMapActivity activity = (MainMapActivity) getActivity();
        if (null != activity) {
            activity.gotoFragment(MainMapActivity.TAG_MAIN_FRAGMENT);
        }
    }

    private void routeReverse() {
        // 起点-终点交换，若起点或终点未设置，则交换输入框文本
        if (null != mStartPoi && null != mEndPoi) {
            PoiItem temp = mStartPoi;
            setStartPoi(mEndPoi);
            setEndPoi(temp);
        } else if (null != mStartPoi) {
            PoiItem temp = mStartPoi;
            setStartPoi(null);  // 要把原交换点置null
            setInputOriginText(mInputDestination.getText().toString());
            setEndPoi(temp);
        } else if (null != mEndPoi) {
            PoiItem temp = mEndPoi;
            setEndPoi(null);    // 要把原交换点置null
            setInputDestText(mInputOrigin.getText().toString());
            setStartPoi(temp);
        }

        // 输入框焦点交换，并且光标移动到文本最后
        if (mInputOrigin.hasFocus()) {
            mInputOrigin.clearFocus();
            mInputDestination.requestFocus();
            moveInputCursorToEnd();
        } else if (mInputDestination.hasFocus()) {
            mInputDestination.clearFocus();
            mInputOrigin.requestFocus();
            moveInputCursorToEnd();
        }

        tryPlanRoute();
    }

    private void removeInputFocus() {
        mInputOrigin.clearFocus();
        mInputDestination.clearFocus();

        InputMethodManager imm = null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(getActivity().getWindow().getDecorView().getWindowToken(), 0);
        }
    }

    private void setStartPoi(PoiItem item) {
        if (null != item) {
            setInputOriginText(item.getTitle());
        } else {
            setInputOriginText("");
        }
        mStartPoi = item;
    }

    private void setEndPoi(PoiItem item) {
        if (null != item) {
            setInputDestText(item.getTitle());
        } else {
            setInputDestText("");
        }
        mEndPoi = item;
    }

    private void setInputOriginText(String text) {
        mInputOrigin.removeTextChangedListener(mRouteResultFragment);
        mInputOrigin.setText(text);
        mInputOrigin.addTextChangedListener(mRouteResultFragment);
    }

    private void setInputDestText(String text) {
        mInputDestination.removeTextChangedListener(mRouteResultFragment);
        mInputDestination.setText(text);
        mInputDestination.addTextChangedListener(mRouteResultFragment);
    }

    private void moveInputCursorToEnd() {
        if (mInputOrigin.hasFocus()) {
            mInputOrigin.setSelection(mInputOrigin.length());
        } else {
            mInputDestination.setSelection(mInputDestination.length());
        }
    }

    private void tryPlanRoute() {
        if (mStartPoi != null && mEndPoi != null) {
            if (!mStartPoi.getPoiId().equals(mEndPoi.getPoiId())) {
                RouteSearch.FromAndTo fromAndTo = new RouteSearch.FromAndTo(mStartPoi.getLatLonPoint(), mEndPoi.getLatLonPoint());

                if (mPlanMode == MODE_DRIVE) {
                    RouteSearch.DriveRouteQuery query = new RouteSearch.DriveRouteQuery(fromAndTo, RouteSearch.
                            DRIVING_MULTI_STRATEGY_FASTEST_SHORTEST_AVOID_CONGESTION, null, null, "");
                    mRouteSearch.calculateDriveRouteAsyn(query);
                } else if (mPlanMode == MODE_BUS) {
                    // 第一个参数表示路径规划的起点和终点，第二个参数表示公交查询模式，第三个参数表示公交查询城市区号，第四个参数表示是否计算夜班车，0表示不计算
                    RouteSearch.BusRouteQuery query = new RouteSearch.BusRouteQuery(fromAndTo,
                            RouteSearch.BUS_DEFAULT,
                            mCurrentCityCode, 0);
                    mRouteSearch.calculateBusRouteAsyn(query);// 异步路径规划公交模式查询
                }
            } else {
                ToastUtil.showToast(getActivity(), "起点和终点不能相同");
            }
        }
    }

    public void showSearchBar() {
        mSearchBar.setVisibility(View.VISIBLE);
    }

    public void hideSearchBar() {
        mSearchBar.setVisibility(View.GONE);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_back:
                backMainFragment();
                break;
            case R.id.btn_swap_route:
                routeReverse();
                break;
            case R.id.input_origin:
            case R.id.input_destination:
                mRouteResultFragment.resetPageNum();
                break;
        }
    }

    @Override
    public void onCheckedChanged(RadioGroup group, int checkedId) {
        switch (checkedId) {
            case R.id.rb_plan_drive:
                mPlanMode = MODE_DRIVE;
                break;
            case R.id.rb_plan_bus:
                mPlanMode = MODE_BUS;
                break;
        }
        tryPlanRoute();
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        if (mInputOrigin.hasFocus()) {
            setStartPoi(mRouteResultFragment.getPoiItem(position));
        } else if (mInputDestination.hasFocus()) {
            setEndPoi(mRouteResultFragment.getPoiItem(position));
        }
        removeInputFocus();
        tryPlanRoute();
    }

    @Override
    public void onBusRouteSearched(BusRouteResult busRouteResult, int i) {
        if (i == 1000) {
            if (busRouteResult != null && busRouteResult.getPaths() != null) {
                if (!busRouteResult.getPaths().isEmpty()) {
                    Bundle data = new Bundle();
                    data.putParcelable("BusRouteResult", busRouteResult);
                    mBusRouteFragment.setArguments(data);
                    switchFragment(mBusRouteFragment);
                } else {
                    ToastUtil.showToast(getActivity(), getString(R.string.no_bus_route_result));
                }
            } else {
                ToastUtil.showToast(getActivity(), getString(R.string.no_bus_route_result));
            }
        }
    }

    @Override
    public void onDriveRouteSearched(DriveRouteResult driveRouteResult, int i) {
        if (i == 1000) {
            MainMapActivity activity = (MainMapActivity) getActivity();
            if (driveRouteResult != null && driveRouteResult.getPaths() != null && activity != null) {
                if (!driveRouteResult.getPaths().isEmpty()) {
                    LatLonPoint startPos = driveRouteResult.getStartPos();
                    LatLonPoint endPos = driveRouteResult.getTargetPos();
                    // 简单导航，只取一条路径
                    activity.showDrivingRoute(driveRouteResult.getPaths().get(0), startPos, endPos);

                    Bundle data = new Bundle();
                    data.putParcelable("DriveRouteResult", driveRouteResult);
                    data.putParcelable("StartPoi", mStartPoi);
                    data.putParcelable("EndPoi", mEndPoi);
                    mDriveRouteFragment.setArguments(data);
                    switchFragment(mDriveRouteFragment);
                } else {
                    ToastUtil.showToast(getActivity(), getString(R.string.no_drive_route_result));
                }
            } else {
                ToastUtil.showToast(getActivity(), getString(R.string.no_drive_route_result));
            }
        }
    }

    @Override
    public void onWalkRouteSearched(WalkRouteResult walkRouteResult, int i) {

    }

    @Override
    public void onRideRouteSearched(RideRouteResult rideRouteResult, int i) {

    }

    @Override
    public void onFocusChange(View v, boolean hasFocus) {
        // 输入框获得焦点
        if (hasFocus) {
            switchFragment(mRouteResultFragment);
        } else {
            if (mStartPoi != null) {
                String str = mInputOrigin.getText().toString();
                if (!mStartPoi.getTitle().equals(str)) {
                    setStartPoi(null);
                }
            }
            if (mEndPoi != null) {
                String str = mInputDestination.getText().toString();
                if (!mEndPoi.getTitle().equals(str)) {
                    setEndPoi(null);
                }
            }
        }
    }
}