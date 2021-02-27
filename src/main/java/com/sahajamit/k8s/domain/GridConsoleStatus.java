package com.sahajamit.k8s.domain;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.Data;
import lombok.ToString;

@Data
@ToString(includeFieldNames = true)
public class GridConsoleStatus {
	
	private int sessionCount;
    private int maxSession;
    private int slotCount;

	private List<String> deadNodes = new ArrayList<String>();

	private Map<String, List<String>> timeoutSessions = new HashMap<String, List<String>>();
	 
	public Map<String, List<String>> getTimeoutSessions() {
		return timeoutSessions;
	}

	public void addTimeoutSessions(String nodeUri, String sessiondId) {
		List<String> nodeSessions = timeoutSessions.get(nodeUri);
		if(nodeSessions == null) nodeSessions = new ArrayList<String>();
		nodeSessions.add(sessiondId);
		timeoutSessions.put(nodeUri, nodeSessions);
	}
 	
	public List<String> getDeadNodes() {
		return deadNodes;
	}

	public boolean addDeadNode(String _nodeUri) {
		if(deadNodes.contains(_nodeUri)) return false;
		return deadNodes.add(_nodeUri);			
	}
 
	public int getSessionCount() {
		return sessionCount;
	}

	public void setSessionCount(int _sessionCount) {
		sessionCount = _sessionCount;
	}

	public int getMaxSession() {
		return maxSession;
	}

	public void setMaxSession(int _maxSession) {
		maxSession = _maxSession;
	}

	public void setSlotCount(int _slotCount) {
		slotCount = _slotCount;
	}

	public int getSlotCount() {
		return slotCount;
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
