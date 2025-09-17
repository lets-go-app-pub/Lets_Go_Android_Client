@file:Suppress("ClassName", "unused")

package site.letsgoapp.letsgo.standAloneObjects.loginFunctions

object login_functions_called_from
/**
 * Login called from
 * 1) LetsGoApplicationClass
 * 2) MainActivity (If Token_Expired)
 * 3) AppActivity (If Token_Expired)
 * 4) ChatStreamWorker
 * 5) CleanDatabaseWorker
 * 6) LoginGetPhoneNumberFragment
 * 7) VerifyPhoneNumbersFragment
 * 8) LoginSelectMethodFragment
 * 9) runLoginAfterDelay(), which can be called from below locations
 *   1) load balancing - CONNECTION_ERROR
 *   2) load balancing - SERVER_DOWN
 *   3) login return value - VALUE_NOT_SET, UNKNOWN
 *   4) iconsResult || picturesResult - CONNECTION_ERROR
 *   5) iconsResult || picturesResult - SERVER_DOWN
 *   6) LoginAccountStatus.DATABASE_DOWN
 *   7) GrpcAndroidSideErrorsEnum.CONNECTION_ERROR
 *   8) GrpcAndroidSideErrorsEnum.SERVER_DOWN
 */
