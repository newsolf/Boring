package com.susion.boring.music;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;

import com.susion.boring.R;
import com.susion.boring.base.BaseFragment;
import com.susion.boring.base.OnLastItemVisibleListener;
import com.susion.boring.base.view.LoadMoreRecycleView;
import com.susion.boring.base.view.LoadMoreView;
import com.susion.boring.db.DbManager;
import com.susion.boring.db.model.SimpleSong;
import com.susion.boring.db.operate.DbBaseOperate;
import com.susion.boring.http.APIHelper;
import com.susion.boring.http.MusicServices;
import com.susion.boring.music.adapter.MusicPageAdapter;
import com.susion.boring.music.itemhandler.MusicPageConstantIH;
import com.susion.boring.music.model.GetPlayListResult;
import com.susion.boring.music.model.MusicPageConstantItem;
import com.susion.boring.music.model.PlayList;
import com.susion.boring.music.model.SimpleTitle;
import com.susion.boring.music.model.Song;
import com.susion.boring.music.presenter.FileDownloadPresenter;
import com.susion.boring.music.service.MusicInstruction;
import com.susion.boring.music.view.MusicControlPanel;
import com.susion.boring.utils.BroadcastUtils;
import com.susion.boring.utils.RVUtils;
import com.susion.boring.utils.SPUtils;
import com.susion.boring.utils.UIUtils;
import com.susion.boring.view.SearchBar;

import java.util.ArrayList;
import java.util.List;

import rx.Observer;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

/**
 * Created by susion on 17/1/19.
 */
public class MusicPageFragment extends BaseFragment implements OnLastItemVisibleListener{

    private List<Object> mData = new ArrayList<>();

    private SearchBar mSearchBar;
    private LoadMoreRecycleView mRV;
    private MusicControlPanel mControlView;
    private Song mSong;
    private ClientMusicReceiver mReceiver;

    private final int PLAY_LIST_PAGE_SIZE = 6;
    private int page = 0;


    @Override
    public View initContentView(LayoutInflater inflater) {
        mView = inflater.inflate(R.layout.fragment_music_page_layout, null);
        initView();
        return mView;
    }

    private void initView() {
        mSearchBar = (SearchBar) mView.findViewById(R.id.search_bar);
        mSearchBar.setJumpToSearchPage(true);
        mSearchBar.setBackground(R.color.colorLightPrimary);

        mRV = (LoadMoreRecycleView) mView.findViewById(R.id.list_view);
        mRV.setLayoutManager(RVUtils.getStaggeredGridLayoutManager(2));
        mRV.setOnLastItemVisibleListener(this);

        mControlView = (MusicControlPanel)mView.findViewById(R.id.music_control_view);
    }

    @Override
    public void initListener() {
        mControlView.seMusicControlListener(new MusicControlPanel.MusicControlViewListener() {
            @Override
            public void onPlayClick(boolean isPlay) {
                if (isPlay) {
                    BroadcastUtils.sendIntentAction(getActivity(), MusicInstruction.SERVICE_RECEIVER_PLAY_MUSIC);
                } else {
                    BroadcastUtils.sendIntentAction(getActivity(), MusicInstruction.SERVICE_RECEIVER_PAUSE_MUSIC);
                }
            }
            @Override
            public void onNextClick() {
                mControlView.setPlay(false);
                mControlView.setMusic(mSong);
            }
        });
    }

    @Override
    public void initData() {
        //init receiver and start service
        mReceiver = new ClientMusicReceiver();
        LocalBroadcastManager.getInstance(getActivity()).registerReceiver(mReceiver, mReceiver.getIntentFilter());
        MusicInstruction.startMusicPlayService(getActivity());

        loadPLayHistory();

        initConstantItem();
        MusicPageAdapter mAdapter = new MusicPageAdapter(getActivity(), mData);
        mRV.setAdapter(mAdapter);

        loadMusicRecommendList();
    }

    @Override
    public void onResume() {
        super.onResume();
        getCurrentPlayMusic();
    }

    private void loadMusicRecommendList() {
        mRV.setLoadStatus(LoadMoreView.LOADING);
        APIHelper.subscribeSimpleRequest(APIHelper.getMusicServices().getPlayList(page * PLAY_LIST_PAGE_SIZE, PLAY_LIST_PAGE_SIZE), new Observer<GetPlayListResult>() {
            @Override
            public void onCompleted() {

            }

            @Override
            public void onError(Throwable e) {
            }

            @Override
            public void onNext(GetPlayListResult playLists) {
                mRV.setLoadStatus(LoadMoreView.NO_LOAD);
                mData.addAll(playLists.getPlaylists());
                page++;
                mRV.getAdapter().notifyDataSetChanged();
            }
        });
    }


    private void loadPLayHistory() {
        String songId = SPUtils.getStringFromMusicConfig(SPUtils.MUSIC_CONFIG_LAST_PLAY_MUSIC, getActivity());
        DbBaseOperate<SimpleSong> dbOperator = new DbBaseOperate<>(DbManager.getLiteOrm(), getContext(), SimpleSong.class);
        dbOperator.query(songId).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(new Observer<SimpleSong>() {
            @Override
            public void onCompleted() {

            }

            @Override
            public void onError(Throwable e) {

            }

            @Override
            public void onNext(SimpleSong song) {
                if (song != null) {
                    mSong = song.translateToSong();
                    mControlView.setMusic(mSong);
                    mControlView.setPlay(false);
                }
            }
        });
    }


    private void getCurrentPlayMusic() {
        Intent intent = new Intent(MusicInstruction.SERVICE_CURRENT_PLAY_MUSIC);
        BroadcastUtils.sendIntentAction(getActivity(), MusicInstruction.SERVICE_RECEIVER_QUERY_IS_PLAYING);
        LocalBroadcastManager.getInstance(getActivity()).sendBroadcast(intent);
    }

    private void initConstantItem() {
        mData.add(new MusicPageConstantItem(R.mipmap.icon_local_music, "本地音乐", MusicPageConstantIH.LOCAL_MUSIC));
        mData.add(new MusicPageConstantItem(R.mipmap.icon_my_collect, "我的喜欢", MusicPageConstantIH.MY_COLLECT));
        mData.add(new SimpleTitle());
    }

    private void loadMusic(boolean autoPlay) {
        Intent intent = new Intent(MusicInstruction.SERVICE_LOAD_MUSIC_INFO);
        intent.putExtra(MusicInstruction.SERVICE_PARAM_PLAY_SONG, mSong);
        intent.putExtra(MusicInstruction.SERVICE_PARAM_PLAY_SONG_AUTO_PLAY, autoPlay);
        LocalBroadcastManager.getInstance(getActivity()).sendBroadcast(intent);
    }

    @Override
    public void onLastItemVisible() {
        loadMusicRecommendList();
    }


    public class ClientMusicReceiver extends BroadcastReceiver {

        public ClientMusicReceiver() {
        }

        public IntentFilter getIntentFilter(){
            IntentFilter filter = new IntentFilter();
            filter.addAction(MusicInstruction.CLIENT_RECEIVER_CURRENT_PLAY_MUSIC);
            filter.addAction(MusicInstruction.CLIENT_RECEIVER_CURRENT_SERVER_STATE);
            filter.addAction(MusicInstruction.CLIENT_RECEIVER_CURRENT_IS_PALING);
            return filter;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            switch (action){
                case MusicInstruction.CLIENT_RECEIVER_CURRENT_PLAY_MUSIC:
                    mSong = (Song) intent.getSerializableExtra(MusicInstruction.CLIENT_PARAM_CURRENT_PLAY_MUSIC);
                    if (mSong != null) {
                        mControlView.setMusic(mSong);
                    }
                    break;
                case MusicInstruction.CLIENT_RECEIVER_CURRENT_IS_PALING:
                    boolean playStatus = intent.getBooleanExtra(MusicInstruction.CLIENT_PARAM_IS_PLAYING, false);
                    boolean needLoadMusic = intent.getBooleanExtra(MusicInstruction.CLIENT_PARAM_NEED_LOAD_MUSIC, false);
                    mControlView.setPlay(playStatus);
                    if (!playStatus && needLoadMusic){
                        loadMusic(false);
                    }
                    break;
            }
        }
    }

}
