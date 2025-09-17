package site.letsgoapp.letsgo.applicationActivityFragments.selectLocationScreenFragment

import android.location.Address
import android.location.Geocoder
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import site.letsgoapp.letsgo.R
import site.letsgoapp.letsgo.activities.AppActivity
import site.letsgoapp.letsgo.applicationActivityFragments.SharedApplicationViewModel
import site.letsgoapp.letsgo.databinding.FragmentSelectLocationMapsBinding
import site.letsgoapp.letsgo.globalAccess.GlobalValues
import site.letsgoapp.letsgo.globalAccess.ServiceLocator
import site.letsgoapp.letsgo.utilities.StoreErrorsInterface
import site.letsgoapp.letsgo.utilities.getLocation
import site.letsgoapp.letsgo.utilities.setSafeOnClickListener
import site.letsgoapp.letsgo.utilities.setupStoreErrorsInterface
import java.lang.ref.WeakReference
import java.util.*

class SelectLocationMapsFragment : Fragment() {

    private lateinit var mMap: GoogleMap
    private var applicationActivity: AppActivity? = null

    private var _binding: FragmentSelectLocationMapsBinding? = null
    private val binding get() = _binding!!

    private val sharedApplicationViewModel: SharedApplicationViewModel by activityViewModels() //syntactic sugar for initializing view model

    private lateinit var geoCoder: Geocoder

    private lateinit var locationAddressJob: Job

    private var errorStore: StoreErrorsInterface = setupStoreErrorsInterface()

    private val callback = OnMapReadyCallback { googleMap ->
        /**
         * Manipulates the map once available.
         * This callback is triggered when the map is ready to be used.
         * This is where we can add markers or lines, add listeners or move the camera.
         * In this case, we just add a marker near Sydney, Australia.
         * If Google Play services is not installed on the device, the user will be prompted to
         * install it inside the SupportMapFragment. This method will only be triggered once the
         * user has installed Google Play services and returned to the app.
         */
        mMap = googleMap

        val startingLocation =
            if (GlobalValues.lastUpdatedLocationInfo.initialized) { //if the location has been initialized
                LatLng(
                    GlobalValues.lastUpdatedLocationInfo.latitude,
                    GlobalValues.lastUpdatedLocationInfo.longitude
                )
            } else { //if the location was never initialized
                LatLng(25.0, -90.0) //in the gulf of mexico
            }

        googleMap.moveCamera(
            CameraUpdateFactory.newLatLngZoom(
                startingLocation,
                GlobalValues.MAP_VIEW_INITIAL_ZOOM
            )
        )

        val weakSharedApplicationViewModel = WeakReference(sharedApplicationViewModel)

        val setLocationAddressText: (MutableList<Address>) -> Unit = { addresses ->
            val addressString =
                if (addresses.isNotEmpty()) {
                    // If an address is found, read it into resultMessage
                    val address: Address = addresses[0]
                    val addressParts: ArrayList<String?> = ArrayList()

                    // Fetch the address lines using getAddressLine,
                    // join them, and send them to the thread
                    for (i in 0..address.maxAddressLineIndex) {
                        addressParts.add(address.getAddressLine(i))
                    }

                    TextUtils.join("\n", addressParts)
                } else {
                    "Unknown"
                }

            weakSharedApplicationViewModel.get()?.let {
                CoroutineScope(Dispatchers.Main).launch {
                    it.setLocationAddressText(addressString)
                }
            }
        }

        geoCoder.getLocation(
            startingLocation.latitude,
            startingLocation.longitude,
            { addresses ->
                setLocationAddressText(addresses)
            },
            errorStore
        )

        mMap.setOnCameraIdleListener {

            val loc = mMap.cameraPosition.target

            //save the location for if the user clicks 'send location'
            sharedApplicationViewModel.chatRoomContainer.locationMessageObject.selectLocationCurrentLocation =
                loc
            sharedApplicationViewModel.chatRoomContainer.pinnedLocationObject.selectLocationCurrentLocation =
                loc

            if (this::locationAddressJob.isInitialized && locationAddressJob.isActive) {
                locationAddressJob.cancel()
            }

            locationAddressJob = CoroutineScope(ServiceLocator.globalIODispatcher).launch {
                geoCoder.getLocation(
                    loc.latitude,
                    loc.longitude,
                    { addresses ->
                        setLocationAddressText(addresses)
                    },
                    errorStore
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applicationActivity = requireActivity() as AppActivity
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        geoCoder = Geocoder(requireActivity(), Locale.getDefault())
        _binding = FragmentSelectLocationMapsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment?
        mapFragment?.getMapAsync(callback)

        //if fragment view is not equal to null
        this.view?.let { fragmentView ->

            //if layers image view is not equal to null
            fragmentView.findViewById<ImageView>(R.id.selectLocationScreenMapLayersImageView)
                ?.let { layerImage ->

                    //set on click listener to create menu item
                    layerImage.setSafeOnClickListener {
                        applicationActivity?.showLocationScreenFragmentLayersPopupMenu(
                            layerImage
                        ) { menuItem ->
                            optionSelected(menuItem)
                        }
                    }
                }
        }
    }

    private fun optionSelected(item: MenuItem): Boolean {
        if (!this::mMap.isInitialized) {
            return true
        }

        when (item.itemId) {
            R.id.mapOptionsMenuNormalMap -> {
                mMap.mapType = GoogleMap.MAP_TYPE_NORMAL
                return true
            }
            R.id.mapOptionsMenuHybridMap -> {
                mMap.mapType = GoogleMap.MAP_TYPE_HYBRID
                return true
            }
            R.id.mapOptionsMenuSatelliteMap -> {
                mMap.mapType = GoogleMap.MAP_TYPE_SATELLITE
                return true
            }
            else -> {
                return false
            }
        }
    }

    override fun onDestroyView() {
        applicationActivity?.hideMenus()
        applicationActivity = null
        _binding = null
        super.onDestroyView()
    }
}