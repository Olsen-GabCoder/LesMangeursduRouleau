package com.lesmangeursdurouleau.app.ui.members

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.lesmangeursdurouleau.app.R
import com.lesmangeursdurouleau.app.data.model.User
import com.lesmangeursdurouleau.app.databinding.FragmentMembersBinding
import com.lesmangeursdurouleau.app.databinding.ItemMemberBinding
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.AndroidEntryPoint

class MembersAdapter(
    private val onItemClick: (User) -> Unit
) : ListAdapter<User, MembersAdapter.MemberViewHolder>(UserDiffCallback()) {

    inner class MemberViewHolder(private val binding: ItemMemberBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(member: User) {
            binding.tvMemberUsername.text = member.username.takeIf { it.isNotEmpty() } ?: itemView.context.getString(R.string.username_not_defined)
            binding.tvMemberEmail.text = member.email.takeIf { it.isNotEmpty() } ?: itemView.context.getString(R.string.na)
            Glide.with(itemView)
                .load(member.profilePictureUrl)
                .placeholder(R.drawable.ic_profile_placeholder)
                .error(R.drawable.ic_profile_placeholder)
                .transition(DrawableTransitionOptions.withCrossFade())
                .circleCrop()
                .into(binding.ivMemberPicture)

            itemView.setOnClickListener {
                // Utilisation d'adapterPosition au lieu de bindingAdapterPosition
                val currentPosition = adapterPosition
                if (currentPosition != RecyclerView.NO_POSITION) {
                    onItemClick(getItem(currentPosition))
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemberViewHolder {
        val binding = ItemMemberBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MemberViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MemberViewHolder, position: Int) {
        val member = getItem(position)
        holder.bind(member)
    }
}

class UserDiffCallback : DiffUtil.ItemCallback<User>() {
    override fun areItemsTheSame(oldItem: User, newItem: User): Boolean {
        return oldItem.uid == newItem.uid
    }

    override fun areContentsTheSame(oldItem: User, newItem: User): Boolean {
        return oldItem == newItem
    }
}


@AndroidEntryPoint
class MembersFragment : Fragment() {

    private var _binding: FragmentMembersBinding? = null
    private val binding get() = _binding!!

    private val membersViewModel: MembersViewModel by viewModels()
    private lateinit var membersAdapter: MembersAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMembersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupObservers()
    }

    private fun setupRecyclerView() {
        membersAdapter = MembersAdapter { member ->
            val action = MembersFragmentDirections.actionMembersFragmentToPublicProfileFragment(
                userId = member.uid,
                username = member.username.ifEmpty { null }
            )
            findNavController().navigate(action)
            Log.d("MembersFragment", "Navigation vers le profil public de : ${member.username} (UID: ${member.uid})")
        }
        binding.rvMembers.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = membersAdapter
            setHasFixedSize(true)
        }
    }

    private fun setupObservers() {
        membersViewModel.members.observe(viewLifecycleOwner) { resource ->
            when (resource) {
                is Resource.Loading -> {
                    binding.progressBarMembers.visibility = View.VISIBLE
                    binding.tvErrorMessage.visibility = View.GONE
                    binding.rvMembers.visibility = View.GONE
                    Log.d("MembersFragment", "Chargement des membres...")
                }
                is Resource.Success -> {
                    binding.progressBarMembers.visibility = View.GONE
                    val membersList = resource.data
                    if (membersList.isNullOrEmpty()) {
                        binding.tvErrorMessage.text = getString(R.string.no_members_found)
                        binding.tvErrorMessage.visibility = View.VISIBLE
                        binding.rvMembers.visibility = View.GONE
                        membersAdapter.submitList(emptyList())
                        Log.d("MembersFragment", "Aucun membre trouvé.")
                    } else {
                        binding.tvErrorMessage.visibility = View.GONE
                        binding.rvMembers.visibility = View.VISIBLE
                        membersAdapter.submitList(membersList)
                        Log.d("MembersFragment", "${membersList.size} membres chargés.")
                    }
                }
                is Resource.Error -> {
                    binding.progressBarMembers.visibility = View.GONE
                    binding.tvErrorMessage.text = getString(R.string.error_loading_members, resource.message ?: "Erreur inconnue")
                    binding.tvErrorMessage.visibility = View.VISIBLE
                    binding.rvMembers.visibility = View.GONE
                    membersAdapter.submitList(emptyList())
                    Log.e("MembersFragment", "Erreur de chargement des membres: ${resource.message}")
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.rvMembers.adapter = null
        _binding = null
    }
}