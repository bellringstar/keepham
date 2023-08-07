package com.ssafy.keepham.domain.box.controller;

import com.ssafy.keepham.common.api.Api;
import com.ssafy.keepham.common.error.BoxError;
import com.ssafy.keepham.domain.box.dto.BoxRequest;
import com.ssafy.keepham.domain.box.dto.BoxResponse;
import com.ssafy.keepham.domain.box.dto.BoxSaveRequest;
import com.ssafy.keepham.domain.box.service.BoxService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/boxs")
public class SuperBoxController {
    private final BoxService boxService;

    @Autowired
    public SuperBoxController(BoxService boxService){

        this.boxService = boxService;
    }

    // 함생성
    @Operation(summary = "함생성")
    @PostMapping
    public Api<BoxResponse> createBox(@RequestBody BoxSaveRequest boxSaveRequest){
        System.out.println("111"+boxSaveRequest.getDetailedAddress());
        var res =  boxService.saveBox(boxSaveRequest);
        return Api.OK(res);
    }

    //특정 id의 함 조회
    @Operation(summary = "특정 id의 함 조회")
    @GetMapping("/{boxId}")
    public Api<Object> getBox(@PathVariable Long boxId){

        if(boxId<=0){
            return Api.ERROR(BoxError.BOX_BAD_REQUEST, String.format("[%d]은/는 요휴하지 않는 id형식 입니다. 1이상의 숫자로 요청해 주세요.", boxId));
        }

        var res = boxService.getBoxById(boxId);

        if(res.isValid()){
            return Api.OK(res);
        }
        else {
            return Api.ERROR(BoxError.BOX_NOT_FOUND, String.format("[%d]은/는 삭제된 함의 id 입니다.", boxId));
        }

    }

    //삭제로 변환 안된 함들 조회
    @Operation(summary = "삭제 안된 함들 전체 조회")
    @GetMapping
    public Api<List<BoxResponse>> getAllBox(){
        return  Api.OK(boxService.getAllBox());
    }

    //함 수정
    @Operation(summary = "함 수정")
    @PutMapping("/{boxId}")
    public  Api<Object> updateBox(@PathVariable Long boxId, @RequestBody BoxRequest boxRequest) {
        if (boxId <= 0) {
            return Api.ERROR(BoxError.BOX_BAD_REQUEST, String.format("[%d]은/는 요휴하지 않는 id형식 입니다. 1이상의 숫자로 요청해 주세요.", boxId));
        }
        var res = boxService.updateBox(boxId, boxRequest);
        return Api.OK(res);

    }
    //함 삭제 상태로 전환
    @Operation(summary = "함 삭제")
    @PutMapping("/delete/{boxId}")
    public Api<Object> deleteBox(@PathVariable Long boxId){

        if(boxId<=0){
            return Api.ERROR(BoxError.BOX_BAD_REQUEST, String.format("[%d]은/는 요휴하지 않는 id형식 입니다. 1이상의 숫자로 요청해 주세요.", boxId));
        }

        var res = boxService.deleteBox(boxId);

        return Api.OK(res);

    }



}