package io.github.droidkaigi.confsched2018.presentation.speaker

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.TargetApi
import android.arch.lifecycle.ViewModelProvider
import android.arch.lifecycle.ViewModelProviders
import android.os.Build
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.view.ViewCompat
import android.support.v7.widget.LinearLayoutManager
import android.text.TextUtils
import android.transition.Transition
import android.transition.TransitionInflater
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import android.view.ViewAnimationUtils
import android.view.ViewGroup
import com.xwray.groupie.GroupAdapter
import com.xwray.groupie.ViewHolder
import io.github.droidkaigi.confsched2018.R
import io.github.droidkaigi.confsched2018.databinding.FragmentSpeakerDetailBinding
import io.github.droidkaigi.confsched2018.di.Injectable
import io.github.droidkaigi.confsched2018.model.Session
import io.github.droidkaigi.confsched2018.presentation.NavigationController
import io.github.droidkaigi.confsched2018.presentation.Result
import io.github.droidkaigi.confsched2018.presentation.sessions.item.SimpleSessionsSection
import io.github.droidkaigi.confsched2018.presentation.sessions.item.SpeechSessionItem
import io.github.droidkaigi.confsched2018.util.SessionAlarm
import io.github.droidkaigi.confsched2018.util.ext.observe
import io.github.droidkaigi.confsched2018.util.ext.setLinearDivider
import timber.log.Timber
import javax.inject.Inject

class SpeakerDetailFragment : Fragment(), Injectable {
    private lateinit var binding: FragmentSpeakerDetailBinding
    @Inject lateinit var viewModelFactory: ViewModelProvider.Factory

    private val sessionsSection = SimpleSessionsSection()
    private var isEnterTransitionCanceled: Boolean = false
    @Inject lateinit var navigationController: NavigationController
    @Inject lateinit var sessionAlarm: SessionAlarm

    private val speakerDetailViewModel: SpeakerDetailViewModel by lazy {
        ViewModelProviders.of(this, viewModelFactory).get(SpeakerDetailViewModel::class.java)
    }

    private val revealViewRunnable = Runnable { reveal() }

    private val onFavoriteClickListener = { session: Session.SpeechSession ->
        speakerDetailViewModel.onFavoriteClick(session)
        sessionAlarm.toggleRegister(session)
    }

    private val onFeedbackListener = { session: Session.SpeechSession ->
        navigationController.navigateToSessionsFeedbackActivity(session)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        binding = FragmentSpeakerDetailBinding.inflate(
                inflater,
                container!!,
                false
        )
        activity?.supportStartPostponedEnterTransition()
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setHasOptionsMenu(true)
        setupRecyclerView()
        speakerDetailViewModel.speakerId = arguments!!.getString(EXTRA_SPEAKER_ID)
        speakerDetailViewModel.speakerSessions.observe(this, { result ->
            when (result) {
                is Result.Success -> {
                    val speaker = result.data.first
                    binding.speaker = speaker
                    sessionsSection.updateSessions(result.data.second,
                            onFavoriteClickListener,
                            onFeedbackListener,
                            userIdInDetail = speaker.id)
                }
                is Result.Failure -> {
                    Timber.e(result.e)
                }
            }
        })

        if (!TextUtils.isEmpty(arguments!!.getString(EXTRA_TRANSITION_NAME))
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            initViewTransition(view, savedInstanceState)
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private fun initViewTransition(view: View, savedInstanceState: Bundle?) {
        view.findViewById<View>(R.id.app_bar_background).visibility = INVISIBLE

        ViewCompat.setTransitionName(
                view.findViewById<View>(R.id.speaker_image),
                arguments!!.getString(EXTRA_TRANSITION_NAME))

        val transitionInflater = TransitionInflater.from(activity)

        if (savedInstanceState == null) {
            activity?.window?.sharedElementEnterTransition = transitionInflater
                    .inflateTransition(R.transition.shared_element_arc)
                    .apply {
                        duration = 400

                        addListener(object : Transition.TransitionListener {
                            override fun onTransitionEnd(p0: android.transition.Transition?) {
                                removeListener(this)
                                // No need to start reveal anim if user pressed back button during shared element transition
                                if (!isEnterTransitionCanceled) {
                                    view.post(revealViewRunnable)
                                }
                            }

                            override fun onTransitionResume(p0: android.transition.Transition?) {
                            }

                            override fun onTransitionPause(p0: android.transition.Transition?) {
                                isEnterTransitionCanceled = true
                            }

                            override fun onTransitionCancel(p0: android.transition.Transition?) {
                                isEnterTransitionCanceled = true
                            }

                            override fun onTransitionStart(p0: android.transition.Transition?) {
                            }
                        })
                    }
        } else {
            view.findViewById<View>(R.id.app_bar_background).visibility = VISIBLE
        }

        activity?.window?.sharedElementReturnTransition = transitionInflater
                .inflateTransition(R.transition.shared_element_arc)
                .apply {
                    duration = 400

                    addListener(object : Transition.TransitionListener {
                        override fun onTransitionEnd(p0: android.transition.Transition?) {
                        }

                        override fun onTransitionResume(p0: android.transition.Transition?) {
                        }

                        override fun onTransitionPause(p0: android.transition.Transition?) {
                        }

                        override fun onTransitionCancel(p0: android.transition.Transition?) {
                        }

                        override fun onTransitionStart(p0: android.transition.Transition?) {
                            removeListener(this)
                            hideReveal()
                        }
                    })
                }
    }

    private fun setupRecyclerView() {
        val groupAdapter = GroupAdapter<ViewHolder>().apply {
            add(sessionsSection)
            setOnItemClickListener({ item, _ ->
                val sessionItem = item as? SpeechSessionItem ?: return@setOnItemClickListener
                navigationController.navigateToSessionDetailActivity(sessionItem.session)
            })
        }
        val linearLayoutManager = LinearLayoutManager(context)
        binding.sessionsRecycler.apply {
            adapter = groupAdapter
            setLinearDivider(R.drawable.shape_divider_vertical_6dp, linearLayoutManager)
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private fun reveal() {
        val revealView = view?.findViewById<View>(R.id.app_bar_background) ?: return
        val speakerImage = view?.findViewById<View>(R.id.speaker_image) ?: return
        val cx = (speakerImage.x + speakerImage.width / 2).toInt()
        val cy = (speakerImage.y + speakerImage.height / 2).toInt()
        ViewAnimationUtils.createCircularReveal(revealView, cx, cy, 0F, revealView.width.toFloat())
                .apply {
                    duration = 400
                    revealView.visibility = View.VISIBLE
                    start()
                }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    fun hideReveal() {
        val revealView = view?.findViewById<View>(R.id.app_bar_background) ?: return
        val speakerImage = view?.findViewById<View>(R.id.speaker_image) ?: return
        val cx = (speakerImage.x + speakerImage.width / 2).toInt()
        val cy = (speakerImage.y + speakerImage.height / 2).toInt()
        ViewAnimationUtils.createCircularReveal(revealView, cx, cy, revealView.width.toFloat(), 0F)
                .apply {
                    duration = 300
                    addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator?) {
                            super.onAnimationEnd(animation)
                            view?.visibility = View.INVISIBLE
                        }
                    })
                    start()
                }
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        if (item?.itemId == android.R.id.home) {
            activity?.onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onDestroyView() {
        view?.removeCallbacks(revealViewRunnable)
        super.onDestroyView()
    }

    companion object {
        const val EXTRA_SPEAKER_ID = "EXTRA_SPEAKER_ID"
        const val EXTRA_TRANSITION_NAME = "EXTRA_TRANSITION_NAME"
        fun newInstance(speakerId: String, transitionName: String?):
                SpeakerDetailFragment = SpeakerDetailFragment()
                .apply {
                    arguments = Bundle().apply {
                        putString(EXTRA_SPEAKER_ID, speakerId)
                        putString(EXTRA_TRANSITION_NAME, transitionName)
                    }
                }
    }
}
