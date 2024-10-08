package onl.netfishers.netshot.rest;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;

import com.augur.tacacs.TacacsException;

import lombok.extern.slf4j.Slf4j;
import onl.netfishers.netshot.Netshot;
import onl.netfishers.netshot.aaa.Tacacs;
import onl.netfishers.netshot.aaa.User;

/**
 * Filter to log requests.
 */
@Slf4j
public class LoggerFilter implements ContainerResponseFilter {

	static private boolean trustXForwardedFor = false;

	static public void init() {
		trustXForwardedFor = Netshot.getConfig("netshot.http.trustxforwardedfor", false);
	}

	@Context
	private HttpServletRequest httpRequest;

	/**
	 * Guess the client IP address based on X-Forwarded-For header (if present).
	 * @return the probable client IP address
	 */
	static public String getClientAddress(HttpServletRequest request) {
		String address = null;
		if (trustXForwardedFor) {
			String forwardedFor = request.getHeader("X-Forwarded-For");
			if (forwardedFor != null) {
				String[] addresses = forwardedFor.split(",");
				address = addresses[0].trim();
			}
		}
		if (address == null) {
			address = request.getRemoteAddr();
		}
		return address;
	}

	@Override
	public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext)
			throws IOException {
		User user = null;
		try {
			user = (User) requestContext.getSecurityContext().getUserPrincipal();
		}
		catch (Exception e) {
			//
		}
		String method = requestContext.getMethod().toUpperCase();
		String remoteAddr = LoggerFilter.getClientAddress(this.httpRequest);
		if ("GET".equals(method)) {
			Netshot.aaaLogger.debug("Request from {} ({}) - {} - \"{} {}\" - {}.", remoteAddr,
					requestContext.getHeaderString(HttpHeaders.USER_AGENT), user == null ? "<none>" : user.getUsername(),
					requestContext.getMethod(), requestContext.getUriInfo().getRequestUri(), responseContext.getStatus());
		}
		else {
			Netshot.aaaLogger.info("Request from {} ({}) - {} - \"{} {}\" - {}.", remoteAddr,
					requestContext.getHeaderString(HttpHeaders.USER_AGENT), user == null ? "<none>" : user.getUsername(),
					requestContext.getMethod(), requestContext.getUriInfo().getRequestUri(), responseContext.getStatus());
			try {
				Tacacs.account(
					requestContext.getMethod(),
					requestContext.getUriInfo().getRequestUri().getPath(),
					user == null ? "<none>" : user.getUsername(),
					Integer.toString(responseContext.getStatus()),
					remoteAddr);
			}
			catch (TacacsException e) {
				log.warn("Unable to send accounting message to TACACS+ server", e);
			}
		}
	}
}