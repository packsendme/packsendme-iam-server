package com.packsendme.microservice.iam.service;

import java.util.Date;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.packsendme.lib.common.constants.HttpExceptionPackSend;
import com.packsendme.lib.common.constants.MicroservicesConstants;
import com.packsendme.lib.common.response.Response;
import com.packsendme.lib.utility.ConvertFormat;
import com.packsendme.microservice.iam.controller.AccountClient;
import com.packsendme.microservice.iam.controller.SMSCodeClient;
import com.packsendme.microservice.iam.dao.UserDAO;
import com.packsendme.microservice.iam.dto.NamesAccountDto;
import com.packsendme.microservice.iam.dto.UserDto;
import com.packsendme.microservice.iam.repository.UserModel;

@Service
@ComponentScan("com.packsendme.lib.utility")
public class UserService {

	@Autowired
	UserDAO userDAO;
	
	@Autowired
	AccountClient accountCliente;

	@Autowired
	SMSCodeClient smscodeClient;

	
	@Autowired
	ConvertFormat formatObj;
	
	public ResponseEntity<?> findUserToLogin(String username, String password) {
		Response<UserDto> responseObj = new Response<UserDto>(0,HttpExceptionPackSend.LOGIN_USER.getAction(), null);
		UserModel entity = new UserModel(); 
		Gson gson = new Gson();
		UserDto userDto = null;
		
		try {
			entity.setUsername(username);
			entity.setPassword(password);
			entity = userDAO.find(entity);
			if(entity != null) {
				userDto = new UserDto(entity);
				ResponseEntity<?> opResultAccount = accountCliente.loadFirstNameAccount(username);
				if(opResultAccount.getStatusCode() == HttpStatus.OK) {
					String json = opResultAccount.getBody().toString();
					NamesAccountDto namesDto = gson.fromJson(json, NamesAccountDto.class);
					userDto.setFirstName(namesDto.getFirstName());
					userDto.setLastName(namesDto.getLastName());
				}
				responseObj = new Response<UserDto>(0,HttpExceptionPackSend.LOGIN_USER.getAction(), userDto);
				return new ResponseEntity<>(responseObj, HttpStatus.FOUND);
			}
			else {
				return new ResponseEntity<>(responseObj, HttpStatus.NOT_FOUND);
			}
		}
		catch (Exception e) {
			e.printStackTrace();
			return new ResponseEntity<>(responseObj, HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}
	
	public ResponseEntity<?> updateUsernameByValidateSMSCode(String username, String usernameNew, String smsCode, String dtAction) {
		Response<UserModel> responseUpdateObj = new Response<UserModel>(0,HttpExceptionPackSend.UPDATE_ACCOUNT.getAction(), null);
		Response<UserModel> responseSMSObj = new Response<UserModel>(0,HttpExceptionPackSend.FOUND_SMS_CODE.getAction(), null);
		ResponseEntity<?> httpResponse = null;
		try {
			
			try {
				httpResponse = smscodeClient.validateSMSCode(usernameNew, smsCode);
			}
			catch (Exception e) {
				if (e.getMessage().equals(HttpStatus.NOT_FOUND+" "+HttpStatus.NOT_FOUND.getReasonPhrase())) {
					return new ResponseEntity<>(responseUpdateObj, HttpStatus.NOT_FOUND);
				}
				else {
					return new ResponseEntity<>(responseUpdateObj, HttpStatus.INTERNAL_SERVER_ERROR);
				}
			}
			if(httpResponse.getStatusCode() == HttpStatus.OK) {
				UserModel entityFind = new UserModel();
				entityFind.setUsername(username);
				UserModel entity = userDAO.find(entityFind);

				if(entity != null) {
					entity.setUsername(usernameNew);
					entity.setActivationKey(MicroservicesConstants.ACTIVATIONKEY);
					entity.setDateUpdate(formatObj.convertStringToDate(dtAction));
					userDAO.update(entity);
					
					// Call AccountMicroservice - Update Username - Account
					httpResponse = accountCliente.changeUsernameForAccount(username,usernameNew,dtAction);
					System.out.println(" Start changeUsernameForAccount  "+ httpResponse.getStatusCode());

					if(httpResponse.getStatusCode() == HttpStatus.OK) {
						return new ResponseEntity<>(responseUpdateObj, HttpStatus.OK);
					}
					// Erro AccountService - Compensaçao de resultado
					else {
						System.out.println(" Compensaçao IAM  "+ httpResponse.getStatusCode());

						entity.setUsername(username);
						entity.setDateUpdate(formatObj.convertStringToDate(dtAction));
						userDAO.update(entity);
						return new ResponseEntity<>(responseUpdateObj, HttpStatus.INTERNAL_SERVER_ERROR);
					}
				}
				else {
					return new ResponseEntity<>(responseUpdateObj, HttpStatus.NOT_FOUND);
				}
			}
			else{
				return new ResponseEntity<>(responseSMSObj, HttpStatus.NOT_FOUND);
			}
		}
		catch (Exception e) {
			e.printStackTrace();
			return new ResponseEntity<>(responseUpdateObj, HttpStatus.INTERNAL_SERVER_ERROR);
		}
		
	}
	
	public ResponseEntity<?> updatePasswordByUsername(UserDto user) {
		Response<UserModel> responseObj = new Response<UserModel>(0,HttpExceptionPackSend.UPDATE_PASSWORD.getAction(), null);

		UserModel entityFind = new UserModel();
		entityFind.setUsername(user.getUsername());
		System.out.println(" updatePasswordByUsername - USERNAME "+ entityFind.getUsername());

		try {
			UserModel entity = userDAO.find(entityFind);
			System.out.println(" updatePasswordByUsername - find "+ entityFind.getUsername());

			if(entity != null) {
				entity.setPassword(user.getPassword());
				entity.setDateUpdate(formatObj.convertStringToDate(user.getDateOperation()));
				System.out.println(" updatePasswordByUsername - password "+ entityFind.getPassword());

				userDAO.update(entity);
				return new ResponseEntity<>(responseObj, HttpStatus.OK);
			}
			else {
				return new ResponseEntity<>(responseObj, HttpStatus.NOT_FOUND);
			}
		}
		catch (Exception e) {
			return new ResponseEntity<>(responseObj, HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}
	
	
	public ResponseEntity<?> cancelUserAccessByUsername(String username, String dtAction) throws Exception {
		Response<UserModel> responseObj = new Response<UserModel>(0,HttpExceptionPackSend.CANCEL_USERNAME.getAction(), null);
		//Convert Date from String to Date
		Date dtNow = formatObj.convertStringToDate(dtAction);
		UserModel entity = new UserModel(); 

		try {
			entity.setUsername(username);
			entity = userDAO.find(entity);

			if(entity != null) {
				entity.setActivated(MicroservicesConstants.USERNAME_ACCOUNT_DISABLED);
				entity.setDateUpdate(dtNow);
				userDAO.update(entity);
				return new ResponseEntity<>(responseObj, HttpStatus.OK);
			}
			else {
				return new ResponseEntity<>(responseObj, HttpStatus.NOT_FOUND);
			}
		}
		catch (Exception e) {
			e.printStackTrace();
			return new ResponseEntity<>(responseObj, HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}
	
		
	public boolean findUserBySMSCodeUsername(String username, String sms) throws Exception {
			UserModel entity = new UserModel(); 
			entity.setUsername(username);
			entity.setActivationKey(sms);
			entity = userDAO.find(entity);
			
			if(entity == null) {
				return MicroservicesConstants.SMS_VALIDATE_NOTFOUND;
			}
			else{
				return MicroservicesConstants.SMS_VALIDATE_FOUND;
			}
	}
		
}
