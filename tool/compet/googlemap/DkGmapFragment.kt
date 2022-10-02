/*
 * Copyright (c) 2017-2020 DarkCompet. All rights reserved.
 */
package tool.compet.googlemap

import android.graphics.Bitmap
import android.os.Bundle
import androidx.annotation.CallSuper
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.GoogleMap.SnapshotReadyCallback
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import tool.compet.core.DkLogcats
import tool.compet.core.DkMaths
import tool.compet.core.DkRunner1

/**
 * Google map fragment.
 */
abstract class DkGmapFragment : SupportMapFragment(), OnMapReadyCallback {
	// Config-data (lost after configuration changed)
	@JvmField protected var map: GoogleMap? = null

	// Non-config data (remain after configuration changed)
	protected lateinit var data: NonConfigData

	companion object {
		const val DEFAULT_ZOOM_LEVEL = 17
	}

	class NonConfigData : ViewModel() {
		var targetPosition: LatLng? = null // target camera position
		var bearing = 0f // camera angle in degrees
		var zoomLevel = DEFAULT_ZOOM_LEVEL.toFloat() // current camera zoom level
		var zoomDiff = 0.5f // zoom step for change `zoomLevel`
		var is3d = false // 3d map or not
		var tilt = 0f // is3d ? 90f : 0f
	}

	override fun onCreate(bundle: Bundle?) {
		super.onCreate(bundle)

		// Obtain own non-config ViewModel
		data = ViewModelProvider(this).get(NonConfigData::class.java)

		// Obtain map at this time
		// Subclass should override `onMapReady` to receive the map
		super.getMapAsync(this)
	}

	@CallSuper
	override fun onMapReady(map: GoogleMap) {
		this.map = map

		data.targetPosition = map.cameraPosition.target
	}

	/**
	 * @return TRUE if the map was successful obtained.
	 */
	val isMapReady: Boolean
		get() = map != null

	/**
	 * Get the map or Async obtain it if not existed.
	 */
	fun obtainMap(callback: DkRunner1<GoogleMap?>) {
		if (map != null) {
			callback.run(map)
		}
		else {
			super.getMapAsync { resultMap -> callback.run(resultMap) }
		}
	}

	/**
	 * Setup zoom amount between 2 zooms.
	 *
	 * @param diff Should be positive.
	 */
	fun setZoomDiff(diff: Float) {
		data.zoomDiff = Math.abs(diff)
	}

	/**
	 * @return Current camera location.
	 */
	val cameraPosition: LatLng?
		get() {
			return map?.cameraPosition?.target
		}

	/**
	 * Add a marker into map.
	 */
	fun addMarker(icon: Bitmap, latLng: LatLng): Marker? {
		return map?.addMarker(MarkerOptions()
			.position(latLng)
			.icon(BitmapDescriptorFactory.fromBitmap(icon))
			.anchor(.5f, .5f)
		)
	}

	/**
	 * Remove a marker if existed from map.
	 */
	fun removeMarker(marker: Marker?) {
		marker?.remove()
	}

	/**
	 * Remove old marker if existed and Set new marker with given icon, position.
	 *
	 * @return New added marker if succeed.
	 */
	fun replaceMarker(marker: Marker?, icon: Bitmap?, position: LatLng?): Marker? {
		marker?.remove()

		return if (icon != null && position != null) {
			addMarker(icon, position)
		}
		else null
	}

	/**
	 * Set declination at current camera position.
	 */
	fun setTilt(tilt: Float) {
		data.tilt = tilt

		moveCameraWithCurrentConfig()
	}

	/**
	 * Set 3d-map at current camera position.
	 */
	fun set3d(turnOn: Boolean, degrees: Float) {
		data.is3d = turnOn
		data.bearing = degrees

		moveCameraWithCurrentConfig()
	}

	fun is3D(): Boolean {
		return data.is3d
	}

	/**
	 * Set map type.
	 *
	 * @param mapType GoogleMap.MAP_TYPE_*
	 */
	fun setMapType(mapType: Int): DkGmapFragment {
		map?.apply {
			this.mapType = mapType
		}
		return this
	}

	/**
	 * Add circle to map at given: position, radius, stroke info...
	 */
	fun addCircle(position: LatLng, radius: Double, strokeWidth: Float, strokeColor: Int) {
		map?.addCircle(
			CircleOptions().center(position)
				.radius(radius)
				.strokeWidth(strokeWidth)
				.strokeColor(strokeColor)
				.visible(true)
		)
	}

	/**
	 * Async get bitmap of current map.
	 */
	fun getSnapshot(callback: DkRunner1<Bitmap?>) {
		val map = map
		try {
			if (map == null) {
				callback.run(null)
			}
			else {
				// This maybe take long time or never callback to us if user does not enable location service.
				// After lookup the source code, this maybe throw exception so we need try/catch for safe.
				map.snapshot(SnapshotReadyCallback { bitmap -> callback.run(bitmap) })
			}
		}
		catch (e: Throwable) {
			DkLogcats.error(this, e, "Could not get map snapshot")
			callback.run(null)
		}
	}

	fun showMap(show: Boolean) {
		val pfm = parentFragmentManager
		if (show) {
			pfm.beginTransaction().show(this).commit()
		}
		else {
			pfm.beginTransaction().hide(this).commit()
		}
	}

	/**
	 * Zoom in, out at current camera position.
	 */
	fun zoomInOut(larger: Boolean) {
		zoomTo(data.zoomLevel + (if (larger) data.zoomDiff else -data.zoomDiff))
	}

	/**
	 * Zoom to given level at current camera position.
	 */
	fun zoomTo(newZoomLevel: Float) {
		this.map?.let { map ->
			data.zoomLevel = DkMaths.clamp(newZoomLevel, map.minZoomLevel, map.maxZoomLevel)
			val level = data.zoomLevel
			map.animateCamera(CameraUpdateFactory.zoomTo(level))
		}
	}

	/**
	 * Move camera position to given `dst`.
	 */
	fun moveCameraTo(dst: LatLng) {
		map?.moveCamera(CameraUpdateFactory.newLatLngZoom(dst, data.zoomLevel))
	}

	/**
	 * Rotate map at current camera position.
	 *
	 * Note: take care of using this since map's camera position maybe
	 * changed (moved) when we rotate or move map.
	 */
	fun rotateMap(degrees: Float) {
		data.bearing = degrees
		moveCameraWithCurrentConfig()
	}

	/**
	 * Rotate the map with given rotation at target position.
	 *
	 * Note: take care of using this since map's camera position maybe
	 * changed (moved) when we rotate or move map.
	 *
	 * @param degrees Rotation degrees in clockwise.
	 * @param targetPos Pass null to rotate at current camera locaion. Otherwise rotate at target position.
	 */
	fun rotateMap(degrees: Float, targetPos: LatLng?) {
		data.bearing = degrees
		data.targetPosition = targetPos
		moveCameraWithCurrentConfig()
	}

	/**
	 * Only call this when we change camera-position-attributes except `zoom` or `scroll`.
	 *
	 * This will move camera with new camera position which be made from current config.
	 * All known current config as: position (latlng), bearing, zoomLevel, tilt...
	 * will be applied at new camera position.
	 */
	protected fun moveCameraWithCurrentConfig() {
		this.map?.let { map ->
			val data = data
			val builder = CameraPosition.Builder().apply {
				data.targetPosition?.let { targetPosition -> this.target(targetPosition) }
				this.bearing(data.bearing)
				this.tilt(data.tilt)
				this.zoom(data.zoomLevel)
			}

			map.moveCamera(CameraUpdateFactory.newCameraPosition(builder.build()))
		}
	}
}
