package io.horizontalsystems.lightningkit.demo.home

import io.horizontalsystems.lightningkit.LightningKit
import io.horizontalsystems.lightningkit.demo.core.Storage
import io.reactivex.disposables.CompositeDisposable

class HomeInteractor(private val lightningKit: LightningKit, private val storage: Storage) : HomeModule.IInteractor {
    lateinit var delegate: HomeModule.IInteractorDelegate
    private val disposables = CompositeDisposable()

    override fun subscribeToStatusUpdates() {
        lightningKit.statusObservable
            .subscribe {
                delegate.onStatusUpdate(it)
            }
            .let {
                disposables.add(it)
            }
    }

    override fun logout() {
        storage.clearConnectionParams()
        delegate.onLogout()
    }
}