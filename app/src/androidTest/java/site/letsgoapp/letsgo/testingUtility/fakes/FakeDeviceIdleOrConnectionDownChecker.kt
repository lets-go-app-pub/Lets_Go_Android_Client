package site.letsgoapp.letsgo.testingUtility.fakes

import android.content.Context
import site.letsgoapp.letsgo.utilities.DeviceIdleOrConnectionDownCheckerInterface
import site.letsgoapp.letsgo.utilities.DeviceIdleOrConnectionDownEnum

class FakeDeviceIdleOrConnectionDownChecker : DeviceIdleOrConnectionDownCheckerInterface {
    var returnValue = DeviceIdleOrConnectionDownEnum.DEVICE_NETWORK_AVAILABLE

    override fun deviceIdleOrConnectionDown(context: Context): DeviceIdleOrConnectionDownEnum {
        return returnValue
    }
}