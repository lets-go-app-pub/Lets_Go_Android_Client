package site.letsgoapp.letsgo.testingUtility.fakes

import site.letsgoapp.letsgo.repositories.StartDeleteFileInterface

class FakeStartDeleteFileInterface : StartDeleteFileInterface {
    override fun sendFileToWorkManager(pathName: String) {}

    override fun deleteAppPrivateFile(fileName: String) {}
}