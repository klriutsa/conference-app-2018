package io.github.droidkaigi.confsched2018.presentation.sessions

import android.arch.lifecycle.ViewModelProvider
import android.arch.lifecycle.ViewModelProviders
import android.os.Bundle
import android.support.constraint.ConstraintSet
import android.support.transition.TransitionInflater
import android.support.transition.TransitionManager
import android.support.v4.app.Fragment
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.xwray.groupie.GroupAdapter
import com.xwray.groupie.ViewHolder
import io.github.droidkaigi.confsched2018.R
import io.github.droidkaigi.confsched2018.databinding.FragmentRoomSessionsBinding
import io.github.droidkaigi.confsched2018.di.Injectable
import io.github.droidkaigi.confsched2018.model.Room
import io.github.droidkaigi.confsched2018.model.Session
import io.github.droidkaigi.confsched2018.presentation.Result
import io.github.droidkaigi.confsched2018.presentation.common.binding.FragmentDataBindingComponent
import io.github.droidkaigi.confsched2018.presentation.sessions.item.DateSessionsGroup
import io.github.droidkaigi.confsched2018.util.ext.addOnScrollListener
import io.github.droidkaigi.confsched2018.util.ext.observe
import io.github.droidkaigi.confsched2018.util.ext.setTextIfChanged
import timber.log.Timber
import javax.inject.Inject

class RoomSessionsFragment : Fragment(), Injectable {

    private lateinit var binding: FragmentRoomSessionsBinding
    private lateinit var roomName: String
    private val dataBindingComponent = FragmentDataBindingComponent(this)

    private val sessionsGroup = DateSessionsGroup(dataBindingComponent)

    @Inject lateinit var viewModelFactory: ViewModelProvider.Factory
    private val sessionsViewModel: RoomSessionsViewModel by lazy {
        ViewModelProviders.of(this, viewModelFactory).get(RoomSessionsViewModel::class.java)
    }

    private val dayVisibleConstraintSet by lazy {
        ConstraintSet().apply {
            clone(context, R.layout.fragment_all_sessions)
            setVisibility(R.id.day_header, ConstraintSet.VISIBLE)
        }
    }

    private val dayGoneConstraintSet by lazy {
        ConstraintSet().apply {
            clone(context, R.layout.fragment_all_sessions)
            setVisibility(R.id.day_header, ConstraintSet.GONE)
        }
    }

    private val onFavoriteClickListener = { session: Session ->
        // Just for response
        session.isFavorited = !session.isFavorited
        binding.sessionsRecycler.adapter.notifyDataSetChanged()

        sessionsViewModel.onFavoriteClick(session)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        roomName = arguments!!.getString(ARG_ROOM_NAME)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        binding = FragmentRoomSessionsBinding.inflate(inflater, container, false, dataBindingComponent)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        lifecycle.addObserver(sessionsViewModel)

        setupRecyclerView()

        sessionsViewModel.roomName = roomName
        sessionsViewModel.sessions.observe(this, { result ->
            when (result) {
                is Result.Success -> {
                    val sessions = result.data
                    sessionsGroup.updateSessions(sessions, onFavoriteClickListener)
                }
                is Result.Failure -> {
                    Timber.e(result.e)
                }
            }
        })

    }

    private fun setupRecyclerView() {
        val groupAdapter = GroupAdapter<ViewHolder>().apply {
            add(sessionsGroup)
            setOnItemClickListener({ item, view ->
                //TODO
            })
        }
        binding.sessionsRecycler.apply {
            adapter = groupAdapter

            addOnScrollListener(
                    onScrollStateChanged = { _: RecyclerView?, newState: Int ->
                        setDayHeaderVisibility(newState != RecyclerView.SCROLL_STATE_IDLE)
                    },
                    onScrolled = { _, _, _ ->
                        val firstPosition = (layoutManager as LinearLayoutManager).findFirstVisibleItemPosition()
                        val firstDay = sessionsGroup.getDateOrNull(0) ?: return@addOnScrollListener
                        val date = sessionsGroup.getDateOrNull(firstPosition) ?: return@addOnScrollListener
                        val dateOfMonth = date.getDate()
                        val dayTitle = if (dateOfMonth == firstDay.getDate()) R.string.day1 else R.string.day2
                        binding.dayHeader.setTextIfChanged(getString(dayTitle))
                    })
        }
    }

    private fun setDayHeaderVisibility(visibleDayHeader: Boolean) {
        val transition = TransitionInflater
                .from(context)
                .inflateTransition(R.transition.date_header_visibility)
        TransitionManager.beginDelayedTransition(binding.sessionsConstraintLayout, transition)
        val constraintSet = if (visibleDayHeader) dayVisibleConstraintSet else dayGoneConstraintSet
        constraintSet.applyTo(binding.sessionsConstraintLayout)
    }

    companion object {
        private val ARG_ROOM_NAME = "room_name"

        fun newInstance(room: Room): RoomSessionsFragment = RoomSessionsFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_ROOM_NAME, room.name)
            }
        }
    }
}