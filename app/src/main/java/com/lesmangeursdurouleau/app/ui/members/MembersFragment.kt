package com.lesmangeursdurouleau.app.ui.members

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity // Import pour AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs // Import pour Safe Args
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

// MembersAdapter reste inchangé, car il est générique et affiche des User
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

    // Utilisation de Safe Args pour récupérer les arguments passés
    private val args: MembersFragmentArgs by navArgs() // NOUVEL IMPORT ET DÉCLARATION

    private lateinit var membersAdapter: MembersAdapter

    companion object {
        private const val TAG = "MembersFragment" // Ajout d'un TAG pour les logs
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMembersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated: Fragment créé. User ID: ${args.userId}, List Type: ${args.listType}, List Title: ${args.listTitle}")

        // Mettre à jour le titre du fragment et de l'ActionBar en fonction de listTitle
        binding.tvMembersTitle.text = args.listTitle
        (activity as? AppCompatActivity)?.supportActionBar?.title = args.listTitle
        Log.i(TAG, "Titre du fragment et ActionBar mis à jour avec: '${args.listTitle}'")

        setupRecyclerView()
        setupObservers()

        // Déclencher le chargement de la liste en passant les arguments au ViewModel
        // Les arguments seront null si le fragment est appelé depuis le BottomNavigationView par exemple.
        membersViewModel.fetchMembers(args.userId, args.listType)
    }

    private fun setupRecyclerView() {
        membersAdapter = MembersAdapter { member ->
            val action = MembersFragmentDirections.actionMembersFragmentToPublicProfileFragment(
                userId = member.uid,
                username = member.username.ifEmpty { null }
            )
            findNavController().navigate(action)
            Log.d(TAG, "Navigation vers le profil public de : ${member.username} (UID: ${member.uid})")
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
                    Log.d(TAG, "Chargement des membres/listes de suivi...")
                }
                is Resource.Success -> {
                    binding.progressBarMembers.visibility = View.GONE
                    val membersList = resource.data
                    if (membersList.isNullOrEmpty()) {
                        // Adapter le message d'erreur/vide en fonction du type de liste
                        binding.tvErrorMessage.text = when (args.listType) {
                            "followers" -> getString(R.string.no_followers_found) // Utilisé si vide
                            "following" -> getString(R.string.no_following_found) // Utilisé si vide
                            else -> getString(R.string.no_members_found) // Fallback pour la liste générale des membres
                        }
                        binding.tvErrorMessage.visibility = View.VISIBLE
                        binding.rvMembers.visibility = View.GONE
                        membersAdapter.submitList(emptyList())
                        Log.d(TAG, "Aucun utilisateur trouvé pour le type de liste ${args.listType}.")
                    } else {
                        binding.tvErrorMessage.visibility = View.GONE
                        binding.rvMembers.visibility = View.VISIBLE
                        membersAdapter.submitList(membersList)
                        Log.d(TAG, "${membersList.size} utilisateurs chargés pour le type de liste ${args.listType}.")
                    }
                }
                is Resource.Error -> {
                    binding.progressBarMembers.visibility = View.GONE
                    binding.tvErrorMessage.text = getString(R.string.error_loading_members, resource.message ?: "Erreur inconnue")
                    binding.tvErrorMessage.visibility = View.VISIBLE
                    binding.rvMembers.visibility = View.GONE
                    membersAdapter.submitList(emptyList())
                    Log.e(TAG, "Erreur de chargement des membres/listes de suivi: ${resource.message}")
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.rvMembers.adapter = null
        _binding = null
        Log.d(TAG, "onDestroyView: Binding nulifié.")
    }
}