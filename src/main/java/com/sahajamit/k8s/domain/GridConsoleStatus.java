package com.sahajamit.k8s.domain;

import lombok.Data;
import lombok.ToString;

@Data
@ToString(includeFieldNames = true)
public class GridConsoleStatus {
	
	private int sessionCount;
    private int maxSession;

	public int getSessionCount() {
		return sessionCount;
	}

	public void setSessionCount(int _sessionCount) {
		this.sessionCount = _sessionCount;
	}

	public int getMaxSession() {
		return maxSession;
	}

	public void setMaxSession(int _maxSession) {
		this.maxSession = _maxSession;
	}

    // private int availableNodesCount;
    // private int busyNodesCount;
    // private int waitingRequestsCount;
    
	// public void setAvailableNodesCount(int _availableNodesCount) {
    //     this.availableNodesCount=_availableNodesCount;
	// }
	// public int getAvailableNodesCount() {
	// 	return this.availableNodesCount;
	// }
	// public int getBusyNodesCount() {
	// 	return busyNodesCount;
	// }
	// public int getWaitingRequestsCount() {
	// 	return waitingRequestsCount;
	// }
	// public void setBusyNodesCount(int _busyNodesCount) {
	// 	this.busyNodesCount = _busyNodesCount;
	// }
	// public void setWaitingRequestsCount(int _waitingRequestsCount) {
	// 	this.waitingRequestsCount = _waitingRequestsCount;
	// }
}
