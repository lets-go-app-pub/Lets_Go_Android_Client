@file:Suppress("ClassName", "unused")

package site.letsgoapp.letsgo.workers.chatStreamWorker

object summary_notification_notes
/**
 * Written by -Jeremiah-
 * There are several problems with the summary notification so turning it off for now.
 * 1) In API levels before 24 the summary notification will simply take the place of all notifications
 *  that it forms into a 'group'. Instead of being able to see and interact with each notification
 *  individually.
 * 2) When setting an intent, I do not seem to make it go to the individual chat rooms when the
 *  summary notification groups them. Instead I was settling for using SEND_TO_CHAT_ROOM_LIST and
 *  if they clicked on a group notification it would send them to the messenger screen.
 * 3) There is a problem with later API versions. Say I am in 2 chat rooms (when I was reproducing it
 *  chatRoom1 had otherUserA and otherUserB inside it and chatRoom2 had otherUserA inside it).
 *   1. Send a message from chatRoom1 and see the heads up display.
 *   2. Without swiping the top bar down or clicking anything send a message from chatRoom2.
 *   3. It will show 2 heads-up notifications nearly simultaneously. The first is the expected notification (the
 *    message from chatRoom2). The second which ends up on top is what the user sees will be the summary
 *    notification itself showing the message from chatRoom1. This means that the heads-up notifications won't
 *    be consistent with what the user expects.
 * It is also worth noting that according to the Android docs "On Android 7.0 (API level 24) and higher, the
 *  system automatically builds a summary for your group using snippets of text from each notification. The
 *  user can expand this notification to see each separate notification". So the system may generate a summary
 *  notification anyway, however I have not seen any problems with it at the very least.
 * **/
