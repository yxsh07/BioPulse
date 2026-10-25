package org.biopulse.controller;
import java.util.*;import jakarta.servlet.http.*;import jakarta.validation.Valid;import org.springframework.web.bind.annotation.*;import org.springframework.security.authentication.*;import org.springframework.security.core.*;import org.springframework.security.core.context.*;import org.springframework.security.web.context.HttpSessionSecurityContextRepository;import org.springframework.security.web.csrf.CsrfToken;import org.biopulse.dto.Requests.Login;import org.biopulse.security.AccessPolicy;
@RestController @RequestMapping("/api/v1")
public class SessionController {
 private final AuthenticationManager manager;private final AccessPolicy access;
 public SessionController(AuthenticationManager manager,AccessPolicy access){this.manager=manager;this.access=access;}
 @GetMapping("/session") public Map<String,Object> session(Authentication auth,CsrfToken csrf){return Map.of("authenticated",auth!=null,"username",auth==null?"":auth.getName(),"roles",auth==null?List.of():auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList(),"csrfToken",csrf.getToken());}
 @PostMapping("/session") public Map<String,Object> login(@Valid @RequestBody Login r,HttpServletRequest request){
  Authentication auth;try{auth=manager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(r.username(),r.password()));}catch(AuthenticationException e){throw new org.biopulse.exception.ApiException(org.springframework.http.HttpStatus.UNAUTHORIZED,"Invalid credentials");}
  if(request.getSession(false)!=null)request.changeSessionId();var ctx=SecurityContextHolder.createEmptyContext();ctx.setAuthentication(auth);SecurityContextHolder.setContext(ctx);request.getSession().setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,ctx);access.audit("LOGIN","session");return Map.of("username",auth.getName(),"roles",auth.getAuthorities());
 }
 @GetMapping("/health") public Map<String,String> health(){return Map.of("status","UP","purpose","ACADEMIC_RESEARCH_ONLY");}
}