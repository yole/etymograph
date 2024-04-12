import {FormRegisterContext} from "@/components/EtymographForm";
import {useContext} from "react";

export default function FormTextArea(props) {
    const register = useContext(FormRegisterContext)
    return <textarea rows={props.rows} cols={props.cols} {...register(props.id)}/>
}
