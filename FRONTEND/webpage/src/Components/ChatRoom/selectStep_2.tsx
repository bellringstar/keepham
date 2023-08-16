/* eslint-disable react-hooks/exhaustive-deps */
import { useEffect, ReactNode, SyntheticEvent, useState } from "react";
import List from "@mui/material/List";
import ListItem from "@mui/material/ListItem";
import Divider from "@mui/material/Divider";
import ListItemText from "@mui/material/ListItemText";
import Typography from "@mui/material/Typography";
import { Box, Button, IconButton, Tab, Tabs } from "@mui/material";
import { AddBox, IndeterminateCheckBox } from "@mui/icons-material";
import axios from "axios";
import { menuInfo, propsType } from "@/Components/ChatRoom/SelectMenus.tsx";

interface TabPanelProps {
  children?: ReactNode;
  index: number;
  value: number;
}

function CustomTabPanel(props: TabPanelProps) {
  const { children, value, index, ...other } = props;

  return (
    <div
      role="tabpanel"
      hidden={value !== index}
      id={`simple-tabpanel-${index}`}
      aria-labelledby={`simple-tab-${index}`}
      {...other}
    >
      {value === index && (
        <Box>
          <Typography component={"div"}>{children}</Typography>
        </Box>
      )}
    </div>
  );
}

interface simpleMenuInfo {
  menu: string;
  count: number;
  price: number;
}

interface menuSelection {
  room_id: number;
  store_name: string;
  menus: simpleMenuInfo[];
}

function menuListItems(
  menuArray: menuInfo[],
  selectable: boolean,
  setCount: (id: number, count: number) => void
) {
  return menuArray.map((menu) => {
    return (
      <Box key={menu.id}>
        <ListItem alignItems="flex-start">
          <Box
            sx={{
              width: 80,
              height: 80,
              display: "flex",
              justifyContent: "center",
              alignItems: "center",
              backgroundColor: "white",
              overflow: "clip",
              borderRadius: 4,
              boxShadow: 1,
            }}
          >
            <Box
              sx={{
                width: 100,
                height: 60,
                backgroundSize: "cover",
                backgroundImage: `url("${menu.image}")`,
              }}
            ></Box>
          </Box>
          <Box
            sx={{
              width: 252,
              overflow: "clip",
              marginX: 1,
            }}
          >
            <ListItemText primary={menu.name} secondary={<>{menu.price}원</>} />
            <Box
              sx={{
                display: "flex",
                justifyContent: "end",
                alignItems: "center",
              }}
            >
              <IconButton
                disabled={!selectable}
                onClick={() => {
                  setCount(menu.id, menu.count + 1);
                }}
              >
                <AddBox />
              </IconButton>
              <Typography
                sx={{ display: "inline" }}
                component="span"
                variant="h6"
                color="text.primary"
              >
                {menu.count}
              </Typography>

              <IconButton
                disabled={!selectable || menu.count == 0}
                onClick={() => {
                  setCount(menu.id, menu.count - 1);
                }}
              >
                <IndeterminateCheckBox />
              </IconButton>
            </Box>
          </Box>
        </ListItem>
        <Divider variant="inset" component="li" />
      </Box>
    );
  });
}

function SelectStep2(props: propsType) {
  const [isInitial, setIsInitial] = useState(true);
  const [tabIdx, setTabIdx] = useState(0);
  const [selected, setSelected] = useState(false);

  const [userCnt, setUserCnt] = useState(0);
  const [selectedCnt, setSelectedCnt] = useState(0);

  const userNick = sessionStorage.getItem("userNick")!;
  const superUser = props.superUser;

  const tabHandler = (_event: SyntheticEvent, newTabIdx: number) => {
    setTabIdx(newTabIdx);
  };

  const selectedMenuList: menuInfo[] = props.menuList.filter(
    (menu) => menu.count > 0
  );

  const totalPrice = selectedMenuList.reduce((prev, cur) => {
    return prev + cur.price * cur.count;
  }, 0);

  function switchSelectButton() {
    if (isInitial) setIsInitial(false);
    setSelected(!selected);
  }

  useEffect(() => {
    let users: string[] = [];
    let selectedUsers: string[] = [];

    // 유저목록 불러오기
    const loadState = async () => {
      const roomId = props.roomId;
      const url =
        import.meta.env.VITE_URL_ADDRESS + "/api/rooms/" + roomId + "/users";
      try {
        const response = await axios.get(url);
        users = response.data.body;
      } catch (error) {
        console.log(error);
      }

      setUserCnt(users.length);

      // 선택 유저 목록 불러오기
      const url2 =
        import.meta.env.VITE_URL_ADDRESS +
        "/api/payment/storeMenu/user/" +
        roomId;
      try {
        const response = await axios.get(url2);
        selectedUsers = response.data.body;
      } catch (error) {
        console.log(error);
      }

      console.log(users);
      console.log(selectedUsers);

      if (selectedUsers.includes(userNick)) {
        setSelected(true);
      }

      setSelectedCnt(selectedUsers.length);
    };
    loadState();
  }, []);

  useEffect(() => {
    // 메뉴 선택 완료
    const key = "Bearer " + sessionStorage.getItem("AccessToken");

    const confirmSelected = async () => {
      const simpleMenuList: simpleMenuInfo[] = selectedMenuList.map((menu) => {
        return {
          menu: menu.name,
          count: menu.count,
          price: menu.price,
        };
      });
      const userSelection: menuSelection = {
        room_id: props.roomId,
        store_name: props.storeName,
        menus: simpleMenuList,
      };

      const url =
        import.meta.env.VITE_URL_ADDRESS + "/api/payment/storeMenu/user";
      try {
        const response = await axios({
          method: "post",
          url: url,
          data: userSelection,
          headers: {
            Authorization: key,
          },
        });

        console.log(response);
      } catch (error) {
        console.log(error);
      }
    };

    // 메뉴 선택 취소
    const revertSelected = async () => {
      const url =
        import.meta.env.VITE_URL_ADDRESS + "/api/payment/storeMenu/user";
      try {
        const response = await axios({
          method: "delete",
          url: url,
          headers: {
            Authorization: key,
          },
        });

        console.log(response);
      } catch (error) {
        console.log(error);
      }
    };

    if (isInitial) return;
    if (selected) confirmSelected();
    else revertSelected();
  }, [isInitial, selected]);

  return (
    <>
      <Box
        sx={{
          width: "100%",
          height: "calc(100% - 160px)",
          overflow: "auto",
          backgroundColor: "#D8DADF",
        }}
      >
        <List sx={{ width: "100%" }}>
          <CustomTabPanel value={tabIdx} index={0}>
            {menuListItems(props.menuList, !selected, props.setCount)}
          </CustomTabPanel>
          <CustomTabPanel value={tabIdx} index={1}>
            {menuListItems(selectedMenuList, !selected, props.setCount)}
          </CustomTabPanel>
        </List>
      </Box>

      <Box
        sx={{
          width: "100%",
          height: 160,
          backgroundColor: "#B6BAC3",
          padding: 1,
          display: "flex",
          flexDirection: "column",
          justifyContent: "start",
          alignItems: "center",
          gap: 1,
        }}
      >
        <Tabs value={tabIdx} onChange={tabHandler}>
          <Tab label="전체 메뉴" id="0" />
          <Tab label="선택한 메뉴" id="1" />
        </Tabs>
        <Box
          sx={{
            width: "60%",
            display: "flex",
            justifyContent: "space-between",
            alignItems: "start",
            gap: 1,
          }}
        >
          <Box
            sx={{
              width: "60%",
              overflow: "hidden",
            }}
          >
            <Typography variant="body1">총 금액 : {totalPrice}원</Typography>
          </Box>
          <Button
            variant={!selected ? "contained" : "outlined"}
            onClick={switchSelectButton}
          >
            {!selected ? "선택 확인" : "다시 선택"}
          </Button>
        </Box>
        {userNick === superUser ? (
          <Box
            sx={{
              width: "60%",
              display: "flex",
              justifyContent: "space-between",
              alignItems: "start",
              gap: 1,
            }}
          >
            <Box
              sx={{
                width: "60%",
                overflow: "hidden",
                display: "flex",
                flexDirection: "column",
                justifyContent: "center",
                alignItems: "center",
              }}
            >
              <Typography variant="body1">선택완료 / 전체인원</Typography>
              <Typography variant="body1">
                {selectedCnt} / {userCnt}
              </Typography>
            </Box>
            <Button
              variant="contained"
              disabled={selectedCnt !== userCnt}
              onClick={switchSelectButton}
            >
              주문 확정
            </Button>
          </Box>
        ) : (
          <Box
            sx={{
              width: "60%",
              display: "flex",
              justifyContent: "space-between",
              alignItems: "start",
              gap: 1,
            }}
          >
            <Box
              sx={{
                width: "60%",
                overflow: "hidden",
                display: "flex",
                flexDirection: "column",
                justifyContent: "center",
                alignItems: "center",
              }}
            >
              <Typography variant="body1">선택완료 / 전체인원</Typography>
              <Typography variant="body1">
                {selectedCnt} / {userCnt}
              </Typography>
            </Box>
            <Button
              variant="contained"
              disabled={selectedCnt !== userCnt}
              onClick={switchSelectButton}
            >
              구매 확정
            </Button>
          </Box>
        )}
      </Box>
    </>
  );
}

export default SelectStep2;
