package io.horizontalsystems.lightningkit

import com.github.lightningnetwork.lnd.lnrpc.*
import io.horizontalsystems.lightningkit.local.LocalLnd
import io.horizontalsystems.lightningkit.remote.RemoteLnd
import io.horizontalsystems.lightningkit.remote.RemoteLndCredentials
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.functions.BiFunction
import io.reactivex.subjects.PublishSubject

class LightningKit(private val lndNode: ILndNode) {

    val statusObservable: Observable<ILndNode.Status>
        get() = lndNode.statusObservable
    val invoicesObservable: Observable<Invoice>
        get() = lndNode.invoicesObservable().retryWhenStatusIsSyncingOrRunning()
    val channelsObservable: Observable<ChannelEventUpdate>
        get() = lndNode.channelsObservable().retryWhenStatusIsSyncingOrRunning()
    val transactionsObservable: Observable<Transaction>
        get() = lndNode.transactionsObservable().retryWhenStatusIsSyncingOrRunning()
    val walletBalanceObservable: Observable<WalletBalanceResponse>
        get() = transactionsObservable.flatMap {
            getWalletBalance().toObservable()
        }
    val channelBalanceObservable: Observable<ChannelBalanceResponse>
        get() {
            val observables = listOf(
                paymentsObservable,
                invoicesObservable.filter { it.state == Invoice.InvoiceState.SETTLED }.map { Unit },
                channelsObservable.map { Unit }
            )

            return Observable.merge(observables)
                .flatMap {
                    getChannelBalance().toObservable()
                }
        }

    private val paymentsUpdatedSubject = PublishSubject.create<Unit>()

    val paymentsObservable: Observable<ListPaymentsResponse> = paymentsUpdatedSubject
        .flatMap {
            listPayments().toObservable()
        }

    fun getWalletBalance(): Single<WalletBalanceResponse> {
        return lndNode.getWalletBalance()
    }

    fun getChannelBalance(): Single<ChannelBalanceResponse> {
        return lndNode.getChannelBalance()
    }

    fun getOnChainAddress(): Single<NewAddressResponse> {
        return lndNode.getOnChainAddress()
    }

    fun listChannels(): Single<ListChannelsResponse> {
        return lndNode.listChannels()
    }

    fun listClosedChannels(): Single<ClosedChannelsResponse> {
        return lndNode.listClosedChannels()
    }

    fun listPendingChannels(): Single<PendingChannelsResponse> {
        return lndNode.listPendingChannels()
    }

    fun decodePayReq(req: String): Single<PayReq> {
        return lndNode.decodePayReq(req)
    }

    fun payInvoice(invoice: String): Single<SendResponse> {
        return lndNode.payInvoice(invoice)
            .doOnSuccess {
                if (it.paymentError.isEmpty()) {
                    paymentsUpdatedSubject.onNext(Unit)
                }
            }
    }

    fun addInvoice(amount: Long, memo: String): Single<AddInvoiceResponse> {
        return lndNode.addInvoice(amount, memo)
    }

    fun listPayments(): Single<ListPaymentsResponse> {
        return lndNode.listPayments()
    }

    fun listInvoices(pending_only: Boolean = false, offset: Long = 0, limit: Long = 1000, reversed: Boolean = false): Single<ListInvoiceResponse> {
        return lndNode.listInvoices(pending_only, offset, limit, reversed)
    }

    fun unlockWallet(password: String): Single<Unit> {
        return lndNode.unlockWallet(password)
    }

    fun openChannel(nodePubKey: String, amount: Long, nodeAddress: String): Observable<OpenStatusUpdate> {
        return lndNode.connect(nodeAddress, nodePubKey)
            .map { Unit }
            .onErrorResumeNext {
                if (it.message?.contains("already connected to peer") == true) {
                    Single.just(Unit)
                } else {
                    Single.error(it)
                }
            }
            .flatMapObservable {
                lndNode.openChannel(nodePubKey, amount)
            }
    }

    fun closeChannel(channelPoint: String, forceClose: Boolean): Observable<CloseStatusUpdate> {
        return lndNode.closeChannel(channelPoint, forceClose)
    }

    private fun <T> Observable<T>.retryWhenStatusIsSyncingOrRunning(): Observable<T> {
        return this.retryWhen {
            it.zipWith(
                statusObservable.filter { status ->
                    status == ILndNode.Status.SYNCING || status == ILndNode.Status.RUNNING
                },
                BiFunction<Throwable, ILndNode.Status, Unit> { t1, t2 -> Unit })
        }
    }

    fun logout(): Single<Unit> {
        return lndNode.logout()
    }

    companion object {
        private var lightningKitLocalLnd: LightningKit? = null

        fun validateRemoteConnection(remoteLndCredentials: RemoteLndCredentials): Single<Unit> {
            val remoteLndNode = RemoteLnd(remoteLndCredentials)

            return remoteLndNode.validateAsync()
        }

        fun remote(remoteLndCredentials: RemoteLndCredentials): LightningKit {
            val remoteLndNode = RemoteLnd(remoteLndCredentials)
            remoteLndNode.scheduleStatusUpdates()

            return LightningKit(remoteLndNode)
        }

        fun local(filesDir: String, password: String): LightningKit {
            lightningKitLocalLnd?.let { return it }

            val localLnd = LocalLnd(filesDir)
            localLnd.startAndUnlock(password)

            return LightningKit(localLnd)
        }

        fun createLocal(filesDir: String, password: String): Single<List<String>> {
            val localLnd = LocalLnd(filesDir)
            lightningKitLocalLnd = LightningKit(localLnd)

            return localLnd.start()
                .flatMap {
                    localLnd.createWallet(password)
                }
        }

        fun restoreLocal(filesDir: String, password: String, mnemonicList: List<String>): Single<Unit> {
            val localLnd = LocalLnd(filesDir)
            lightningKitLocalLnd = LightningKit(localLnd)

            return localLnd.start()
                .flatMap {
                    localLnd.restoreWallet(mnemonicList, password)
                }
        }
    }
}