package net.taler.wallet


import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.navigation.findNavController

/**
 * A simple [Fragment] subclass.
 */
class AlreadyPaid : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_already_paid, container, false)
        view.findViewById<Button>(R.id.button_success_back).setOnClickListener {
            activity!!.findNavController(R.id.nav_host_fragment).navigateUp()
        }
        return view
    }


}
