import { useState } from "react";
import "./Main.css";
import MapContainer from "@/Components/Main/MapContainer.tsx";
import BoxLists from "@/Components/Main/Getdata.tsx";

function BoxSearch() {
  const [boxLocation, searchlocation] = useState("");
  const [showMode, setShowMode] = useState(false);
  let content = null;
  const changeShowMode = () => {
    setShowMode(true);
  };

  if (showMode) {
    content = (
      <div className="mapcontainer">
        <MapContainer />
        <BoxLists />
      </div>
    );
  }
  return (
    <>
      <div>
        <label>
          <input
            type="text"
            placeholder="현재 위치를 입력하세요"
            value={boxLocation}
            onChange={(e) => searchlocation(e.target.value)}
          />
          <button type="submit" onClick={changeShowMode}>
            검색
          </button>
          {content}
          {boxLocation}
        </label>
      </div>
    </>
  );
}

export default BoxSearch;