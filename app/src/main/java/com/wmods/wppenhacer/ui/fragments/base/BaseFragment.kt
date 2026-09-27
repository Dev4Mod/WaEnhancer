package com.wmods.wppenhacer.ui.fragments.base

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.wmods.wppenhacer.databinding.BaseFragmentBinding

open class BaseFragment : Fragment() {
    private var baseBinding: BaseFragmentBinding? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val binding = BaseFragmentBinding.inflate(inflater, container, false)
        baseBinding = binding
        return binding.root
    }

    fun setDisplayHomeAsUpEnabled(enabled: Boolean) {
        val actionBar = (activity as? AppCompatActivity)?.supportActionBar ?: return
        actionBar.setDisplayHomeAsUpEnabled(enabled)
    }
}
