package com.lesmangeursdurouleau.app.ui.meetings.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.lesmangeursdurouleau.app.data.model.ClubEvent
import com.lesmangeursdurouleau.app.databinding.ItemMeetingBinding
import java.text.SimpleDateFormat
import java.util.Locale

class MeetingListAdapter(private val onItemClicked: (ClubEvent) -> Unit) :
    ListAdapter<ClubEvent, MeetingListAdapter.MeetingViewHolder>(MeetingDiffCallback()) {

    private val dateFormat = SimpleDateFormat("EEEE dd MMMM 'à' HH:mm", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MeetingViewHolder {
        val binding = ItemMeetingBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MeetingViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MeetingViewHolder, position: Int) {
        val meeting = getItem(position)
        holder.bind(meeting)
        holder.itemView.setOnClickListener {
            onItemClicked(meeting)
        }
    }

    inner class MeetingViewHolder(private val binding: ItemMeetingBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(meeting: ClubEvent) {
            binding.tvMeetingTitle.text = meeting.title
            binding.tvMeetingDate.text = dateFormat.format(meeting.date)
            binding.tvMeetingLocation.text = "Lieu : ${meeting.location}"
            binding.tvMeetingDescription.text = meeting.description ?: "Aucune description."
            // Tu peux changer l'icône en fonction du type de réunion (en ligne/physique) plus tard
            // binding.ivMeetingIcon.setImageResource(...)
        }
    }

    class MeetingDiffCallback : DiffUtil.ItemCallback<ClubEvent>() {
        override fun areItemsTheSame(oldItem: ClubEvent, newItem: ClubEvent): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ClubEvent, newItem: ClubEvent): Boolean {
            return oldItem == newItem
        }
    }
}