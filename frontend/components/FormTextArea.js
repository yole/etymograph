import {useFormContext} from "react-hook-form";
import InputAssist from "@/components/InputAssist";

export default function FormTextArea(props) {
    const {register} = useFormContext()
    return <>
        <textarea rows={props.rows} cols={props.cols} id={props.id} {...register(props.id)} className={props.className}/>
        {props.inputAssist && <div><InputAssist id={props.id}/></div>}
    </>
}
