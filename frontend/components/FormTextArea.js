import {useFormContext} from "react-hook-form";

export default function FormTextArea(props) {
    const {register} = useFormContext()
    return <textarea rows={props.rows} cols={props.cols} {...register(props.id)} className={props.className}/>
}
