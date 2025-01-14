package io.horizontalsystems.lightningkit.demo.channels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.github.lightningnetwork.lnd.lnrpc.*
import io.horizontalsystems.lightningkit.ILndNode
import io.horizontalsystems.lightningkit.demo.core.App

object ChannelsModule {
    interface IInteractor {
        fun listChannels()
        fun listClosedChannels()
        fun listPendingChannels()
        fun subscribeToStatusUpdates()
        fun subscribeToChannelUpdates()
        fun closeChannel(channelPoint: String)
        fun clear()
    }

    interface IInteractorDelegate {
        fun onReceiveChannels(info: ListChannelsResponse)
        fun onReceiveClosedChannels(info: ClosedChannelsResponse)
        fun onReceivePendingChannels(info: PendingChannelsResponse)
        fun onReceivedError(e: Throwable)
        fun onStatusUpdate(status: ILndNode.Status)
        fun onChannelsUpdate(channelEventUpdate: ChannelEventUpdate)
        fun closeChannel(channelPoint: String)
        fun onChannelCloseStatusUpdate(closeStatus: CloseStatusUpdate)
        fun onChannelCloseFailure(e: Throwable)
    }

    class Factory : ViewModelProvider.Factory {
        override fun <T : ViewModel?> create(modelClass: Class<T>): T {
            val interactor = ChannelsInteractor(App.lightningKit)
            val presenter = ChannelsPresenter(interactor)

            interactor.delegate = presenter

            return presenter as T
        }
    }
}